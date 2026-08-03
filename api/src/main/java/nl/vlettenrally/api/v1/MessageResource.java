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
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.api.security.TeamScoped;
import nl.vlettenrally.data.domain.AdminNotification;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.domain.MessageTemplate;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.common.SuccessResponse;
import nl.vlettenrally.data.dto.message.*;
import nl.vlettenrally.data.enums.MessageType;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AdminInboxService;
import nl.vlettenrally.service.MessageService;
import nl.vlettenrally.service.MessageTemplateService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Resource for Message operations
 * <p>
 * Team endpoints:
 * - GET /api/v1/messages/team/{teamId} - Get all messages for a team
 * - GET /api/v1/messages/team/{teamId}/unread - Get unread messages count
 * - PUT /api/v1/messages/{messageId}/read - Mark message as read
 * - PUT /api/v1/messages/team/{teamId}/read-all - Mark all messages as read
 * <p>
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
@RolesAllowed({SecurityRoles.TEAM, SecurityRoles.STAFF})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageResource {

    @Inject
    MessageService messageService;

    @Inject
    MessageTemplateService messageTemplateService;

    @Inject
    AdminInboxService adminInboxService;

    @Inject
    TeamRepository teamRepository;


    /**
     * Get a single message by ID
     */
    @GET
    @RolesAllowed(SecurityRoles.TEAM)
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
    @RolesAllowed(SecurityRoles.TEAM)
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
    @RolesAllowed(SecurityRoles.TEAM)
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
    @RolesAllowed(SecurityRoles.TEAM)
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
    @RolesAllowed(SecurityRoles.TEAM)
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
    @RolesAllowed(SecurityRoles.TEAM)
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
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

        // Forcing SMS costs money per team, so it stays tied to the one type the participants are
        // told to treat as an instruction from the desk. Rejecting rather than silently ignoring
        // the flag keeps a mis-typed broadcast from looking like it reached phones.
        if (request.sendViaSms() && !type.isCritical()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("SMS can only be forced for rallydesk messages")
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
                adminId,
                request.sendViaSms()
        );

        // Optionally save as template
        if (request.saveAsTemplate() && request.templateName() != null && !request.templateName().isBlank()) {
            messageTemplateService.create(request.templateName(), type, request.content(), request.icon());
        }

        // Build response with delivery details from batch using service method
        List<Message> messagesInBatch = messageService.getMessagesInBatch(batchResult.batchId());
        AdminMessageResponse response = messageService.buildAdminMessageResponse(batchResult, messagesInBatch);

        return Response.ok(response).build();
    }

    /**
     * Get admin messages for a game with pagination
     */
    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
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
                    return messageService.buildAdminMessageResponse(m, batchMessages);
                })
                .collect(Collectors.toList());

        return Response.ok(new AdminMessageListResponse(messageDtos, page, pageSize, totalCount, totalPages)).build();
    }

    /**
     * Retry failed deliveries for a specific batch
     */
    @POST
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
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
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
    @Path("/admin/templates/{templateId}")
    public Response deleteTemplate(@PathParam("templateId") UUID templateId) {
        messageTemplateService.delete(templateId);
        return Response.ok(new SuccessResponse(true)).build();
    }

    // ==================== Admin Inbox Endpoints ====================

    @GET
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
    @Path("/admin/inbox/games/{gameId}")
    public Response getAdminInboxByGame(
        @PathParam("gameId") UUID gameId,
        @QueryParam("page") @DefaultValue("0") int page,
        @QueryParam("pageSize") @DefaultValue("20") int pageSize
    ) {
        if (page < 0) page = 0;
        if (pageSize < 1 || pageSize > 200) pageSize = 20;

        List<AdminNotification> notifications = adminInboxService.findByGame(gameId, page, pageSize);
        long totalCount = adminInboxService.countByGame(gameId);
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);
        List<AdminInboxNotificationResponse> dtos = notifications.stream().map(this::toInboxDto).toList();
        return Response.ok(new AdminInboxNotificationListResponse(dtos, page, pageSize, totalCount, totalPages)).build();
    }

    @PUT
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
    @Path("/admin/inbox/{notificationId}/read")
    public Response markAdminInboxNotificationAsRead(
        @PathParam("notificationId") UUID notificationId,
        @Context SecurityContext securityContext
    ) {
        adminInboxService.markAsRead(notificationId, adminIdFromContext(securityContext));
        return Response.ok(new SuccessResponse(true)).build();
    }

    @PUT
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.MESSAGE_SENDER, SecurityRoles.RALLY_DESK})
    @Path("/admin/inbox/games/{gameId}/read-all")
    public Response markAllAdminInboxNotificationsAsRead(
        @PathParam("gameId") UUID gameId,
        @Context SecurityContext securityContext
    ) {
        adminInboxService.markAllAsRead(gameId, adminIdFromContext(securityContext));
        return Response.ok(new SuccessResponse(true)).build();
    }

    // ==================== Helper Methods ====================


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

    private AdminInboxNotificationResponse toInboxDto(AdminNotification notification) {
        String teamName = notification.getTeamId() != null
            ? teamRepository.findById(notification.getTeamId()).map(Team::getName).orElse(null)
            : null;
        return new AdminInboxNotificationResponse(
            notification.getId(),
            notification.getGameId(),
            notification.getTeamId(),
            teamName,
            notification.getSource(),
            notification.getContent(),
            notification.getMetadataJson(),
            notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : null,
            notification.isRead(),
            notification.getReadAt() != null ? notification.getReadAt().toString() : null,
            notification.getReadByAdminId()
        );
    }

    private UUID adminIdFromContext(SecurityContext context) {
        if (context == null || context.getUserPrincipal() == null) {
            return null;
        }
        try {
            return UUID.fromString(context.getUserPrincipal().getName());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
