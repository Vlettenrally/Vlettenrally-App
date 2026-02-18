package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.websocket.server.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.TeamScoped;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.common.SuccessResponse;
import nl.vlettenrally.data.dto.message.AdminMessageBatchResult;
import nl.vlettenrally.data.dto.message.AdminMessageListResponse;
import nl.vlettenrally.data.dto.message.AdminMessageResponse;
import nl.vlettenrally.data.dto.message.MessageResponse;
import nl.vlettenrally.data.dto.message.MessageTemplateRequest;
import nl.vlettenrally.data.dto.message.MessageTemplateResponse;
import nl.vlettenrally.data.dto.message.RetryMessageResponse;
import nl.vlettenrally.data.dto.message.SendAdminMessageRequest;
import nl.vlettenrally.data.dto.message.UnreadCountResponse;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.enums.TransmissionChannel;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.MessageService;
import nl.vlettenrally.service.MessageTemplateService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Resource for Message operations
 * 
 * Team endpoints:
 * - GET /api/v1/messages/team/{teamId} - Get all messages for a team
 * - GET /api/v1/messages/team/{teamId}/unread - Get unread messages count
 * - PUT /api/v1/messages/{messageId}/read - Mark message as read
 * - PUT /api/v1/messages/team/{teamId}/read-all - Mark all messages as read
 * 
 * Admin endpoints:
 * - POST /api/v1/messages/admin - Send admin message to teams
 * - GET /api/v1/messages/admin/games/{gameId} - Get admin messages for a game
 * - POST /api/v1/messages/admin/{messageId}/retry - Retry failed deliveries
 * - POST /api/v1/messages/admin/games/{gameId}/retry-all-failed - Retry all failed
 * - GET /api/v1/messages/admin/templates - Get all templates
 * - POST /api/v1/messages/admin/templates - Create template
 * - PUT /api/v1/messages/admin/templates/{templateId} - Update template
 * - DELETE /api/v1/messages/admin/templates/{templateId} - Delete template
 */
@Path("/api/v1/messages")
@RolesAllowed({"team", "admin"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageResource {

    @Inject
    MessageService messageService;

    @Inject
    MessageTemplateService messageTemplateService;

    @Inject
    TeamRepository teamRepository;

    /**
     * Get a single message by ID
     */
    @GET
    @RolesAllowed("team")
    @Path("/{messageId}")
    public Response getMessage(@PathParam("messageId") UUID messageId) {
        Optional<Message> message = messageService.findById(messageId);
        if (message.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(toDto(message.get())).build();
    }

    /**
     * Get all messages for a team
     */
    @GET
    @RolesAllowed("team")
    @TeamScoped
    @Path("/team/{teamId}")
    public Response getMessagesForTeam(@PathParam("teamId") UUID teamId) {

        List<Message> messages = messageService.findByTeamId(teamId);
        return Response.ok(messages.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get unread messages count for a team
     */
    @GET
    @RolesAllowed("team")
    @TeamScoped
    @Path("/team/{teamId}/unread")
    public Response getUnreadCount(
            @PathParam("teamId") UUID teamId) {

        int count = messageService.countUnreadByTeamId(teamId);
        return Response.ok(new UnreadCountResponse(count)).build();
    }

    /**
     * Mark a message as read
     */
    @PUT
    @RolesAllowed("team")
    @Path("/{messageId}/read")
    public Response markAsRead(
            @PathParam("messageId") UUID messageId) {

        messageService.markAsRead(messageId);
        return Response.ok(new SuccessResponse(true)).build();
    }

    /**
     * Mark multiple messages as read (batch operation)
     */
    @POST
    @RolesAllowed("team")
    @Path("/mark-read")
    public Response markMultipleAsRead(MarkMessagesReadRequest request) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        messageService.markMultipleAsRead(request.ids());
        return Response.ok(new SuccessResponse(true)).build();
    }

    public record MarkMessagesReadRequest(List<UUID> ids) {}

    /**
     * Mark all messages as read for a team
     */
    @PUT
    @RolesAllowed("team")
    @TeamScoped
    @Path("/team/{teamId}/read-all")
    public Response markAllAsRead(
            @PathParam("teamId") UUID teamId) {

        messageService.markAllAsReadForTeam(teamId);
        return Response.ok(new SuccessResponse(true)).build();
    }

    private MessageResponse toDto(Message message) {
        return new MessageResponse(
            message.getId(),
            message.getTeamId(),
            message.getMessageType() != null ? message.getMessageType().name().toLowerCase() : null,
            message.getContent(),
            message.getIcon(),
            message.getSentAt() != null ? message.getSentAt().toString() : null,
            message.isDelivered(),
            message.isRead()
        );
    }

    // ==================== Admin Endpoints ====================

    /**
     * Send an admin message to teams
     */
    @POST
    @RolesAllowed("admin")
    @Path("/admin")
    public Response sendAdminMessage(SendAdminMessageRequest request, @Context SecurityContext securityContext) {
        MessageType type;
        try {
            type = MessageType.valueOf(request.messageType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid message type: " + request.messageType())
                    .build();
        }

        UUID adminId = securityContext.getUserPrincipal() != null
                ? UUID.fromString(securityContext.getUserPrincipal().getName())
                : null;

        AdminMessageBatchResult batchResult = messageService.sendAdminMessage(
                request.gameId(),
                request.teamIds(),
                type,
                request.content(),
                request.icon(),
                adminId
        );

        // Optionally save as template
        if (request.saveAsTemplate() && request.templateName() != null && !request.templateName().isBlank()) {
            messageTemplateService.create(request.templateName(), type, request.content(), request.icon());
        }

        // Build response with delivery details from batch
        List<Message> messagesInBatch = messageService.getMessagesInBatch(batchResult.batchId());
        AdminMessageResponse response = toAdminDtoFromBatch(batchResult, messagesInBatch);

        return Response.ok(response).build();
    }

    /**
     * Get admin messages for a game with pagination
     */
    @GET
    @RolesAllowed("admin")
    @Path("/admin/games/{gameId}")
    public Response getAdminMessagesForGame(
            @PathParam("gameId") UUID gameId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize) {

        List<Message> representativeMessages = messageService.findAdminMessagesByGame(gameId, page, pageSize);
        long totalCount = messageService.countAdminMessagesByGame(gameId);
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        List<AdminMessageResponse> messageDtos = representativeMessages.stream()
                .map(m -> {
                    List<Message> batchMessages = messageService.getMessagesInBatch(m.getBatchId());
                    return toAdminDtoFromMessages(m, batchMessages);
                })
                .collect(Collectors.toList());

        return Response.ok(new AdminMessageListResponse(messageDtos, page, pageSize, totalCount, totalPages)).build();
    }

    /**
     * Retry failed deliveries for a specific batch
     */
    @POST
    @RolesAllowed("admin")
    @Path("/admin/{batchId}/retry")
    public Response retryFailedDeliveries(@PathParam("batchId") UUID batchId) {
        List<Message> failedBefore = messageService.getMessagesInBatch(batchId).stream()
                .filter(m -> !m.isDelivered())
                .toList();
        
        int successCount = messageService.retryFailedDeliveries(batchId);
        
        return Response.ok(new RetryMessageResponse(
                failedBefore.size(),
                successCount,
                failedBefore.size() - successCount
        )).build();
    }

    /**
     * Retry all failed deliveries for a game
     */
    @POST
    @RolesAllowed("admin")
    @Path("/admin/games/{gameId}/retry-all-failed")
    public Response retryAllFailedDeliveries(@PathParam("gameId") UUID gameId) {
        // Count failed before retry
        List<Message> representativeMessages = messageService.findAdminMessagesByGame(gameId, 0, Integer.MAX_VALUE);
        int totalFailed = representativeMessages.stream()
                .mapToInt(m -> (int) messageService.getMessagesInBatch(m.getBatchId()).stream().filter(msg -> !msg.isDelivered()).count())
                .sum();

        int successCount = messageService.retryAllFailedDeliveries(gameId);

        return Response.ok(new RetryMessageResponse(
                totalFailed,
                successCount,
                totalFailed - successCount
        )).build();
    }

    // ==================== Template Endpoints ====================

    /**
     * Get all message templates
     */
    @GET
    @RolesAllowed("admin")
    @Path("/admin/templates")
    public Response getTemplates() {
        List<MessageTemplate> templates = messageTemplateService.findAll();
        List<MessageTemplateResponse> response = templates.stream()
                .map(this::toTemplateDto)
                .collect(Collectors.toList());
        return Response.ok(response).build();
    }

    /**
     * Create a new message template
     */
    @POST
    @RolesAllowed("admin")
    @Path("/admin/templates")
    public Response createTemplate(MessageTemplateRequest request) {
        MessageType type;
        try {
            type = MessageType.valueOf(request.messageType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid message type: " + request.messageType())
                    .build();
        }

        MessageTemplate template = messageTemplateService.create(
                request.name(),
                type,
                request.content(),
                request.icon()
        );

        return Response.status(Response.Status.CREATED).entity(toTemplateDto(template)).build();
    }

    /**
     * Update a message template
     */
    @PUT
    @RolesAllowed("admin")
    @Path("/admin/templates/{templateId}")
    public Response updateTemplate(@PathParam("templateId") UUID templateId, MessageTemplateRequest request) {
        MessageType type;
        try {
            type = MessageType.valueOf(request.messageType().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid message type: " + request.messageType())
                    .build();
        }

        try {
            MessageTemplate template = messageTemplateService.update(
                    templateId,
                    request.name(),
                    type,
                    request.content(),
                    request.icon()
            );
            return Response.ok(toTemplateDto(template)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    /**
     * Delete a message template
     */
    @DELETE
    @RolesAllowed("admin")
    @Path("/admin/templates/{templateId}")
    public Response deleteTemplate(@PathParam("templateId") UUID templateId) {
        messageTemplateService.delete(templateId);
        return Response.ok(new SuccessResponse(true)).build();
    }

    // ==================== Helper Methods ====================

    /**
     * Convert a batch result and its messages to AdminMessageResponse.
     */
    private AdminMessageResponse toAdminDtoFromBatch(AdminMessageBatchResult batchResult, List<Message> messages) {
        Message firstMessage = messages.isEmpty() ? null : messages.getFirst();
        
        List<AdminMessageResponse.DeliveryDetail> deliveryDetails = messages.stream()
                .map(m -> {
                    Optional<Team> team = teamRepository.findById(m.getTeamId());
                    return new AdminMessageResponse.DeliveryDetail(
                            m.getTeamId(),
                            team.map(Team::getName).orElse("Unknown"),
                            m.isDelivered(),
                            m.getTransmittedVia() != null 
                                    ? m.getTransmittedVia().stream().map(TransmissionChannel::name).collect(Collectors.toSet())
                                    : Set.of(),
                            m.getTransmissionError()
                    );
                })
                .collect(Collectors.toList());

        return new AdminMessageResponse(
                batchResult.batchId(),
                firstMessage != null ? firstMessage.getGameId() : null,
                firstMessage != null && firstMessage.getMessageType() != null ? firstMessage.getMessageType().name().toLowerCase() : null,
                firstMessage != null ? firstMessage.getContent() : null,
                firstMessage != null ? firstMessage.getIcon() : null,
                batchResult.sentAt() != null ? batchResult.sentAt().toString() : null,
                firstMessage != null && firstMessage.getSource() != null ? firstMessage.getSource().name() : null,
                firstMessage != null ? firstMessage.getSentByAdminId() : null,
                batchResult.totalTeams(),
                batchResult.deliveredCount(),
                batchResult.failedCount(),
                deliveryDetails
        );
    }

    /**
     * Convert a representative message and its batch messages to AdminMessageResponse.
     */
    private AdminMessageResponse toAdminDtoFromMessages(Message representativeMessage, List<Message> batchMessages) {
        int deliveredCount = (int) batchMessages.stream().filter(Message::isDelivered).count();
        int failedCount = batchMessages.size() - deliveredCount;

        List<AdminMessageResponse.DeliveryDetail> deliveryDetails = batchMessages.stream()
                .map(m -> {
                    Optional<Team> team = teamRepository.findById(m.getTeamId());
                    return new AdminMessageResponse.DeliveryDetail(
                            m.getTeamId(),
                            team.map(Team::getName).orElse("Unknown"),
                            m.isDelivered(),
                            m.getTransmittedVia() != null 
                                    ? m.getTransmittedVia().stream().map(TransmissionChannel::name).collect(Collectors.toSet())
                                    : Set.of(),
                            m.getTransmissionError()
                    );
                })
                .collect(Collectors.toList());

        return new AdminMessageResponse(
                representativeMessage.getBatchId(),
                representativeMessage.getGameId(),
                representativeMessage.getMessageType() != null ? representativeMessage.getMessageType().name().toLowerCase() : null,
                representativeMessage.getContent(),
                representativeMessage.getIcon(),
                representativeMessage.getSentAt() != null ? representativeMessage.getSentAt().toString() : null,
                representativeMessage.getSource() != null ? representativeMessage.getSource().name() : null,
                representativeMessage.getSentByAdminId(),
                batchMessages.size(),
                deliveredCount,
                failedCount,
                deliveryDetails
        );
    }

    private MessageTemplateResponse toTemplateDto(MessageTemplate template) {
        return new MessageTemplateResponse(
                template.getId(),
                template.getName(),
                template.getMessageType() != null ? template.getMessageType().name().toLowerCase() : null,
                template.getContent(),
                template.getIcon(),
                template.getCreatedAt() != null ? template.getCreatedAt().toString() : null,
                template.getUpdatedAt() != null ? template.getUpdatedAt().toString() : null
        );
    }
}
