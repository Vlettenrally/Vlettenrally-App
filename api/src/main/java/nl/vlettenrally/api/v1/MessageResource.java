package nl.vlettenrally.api.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.service.AuthService;
import nl.vlettenrally.service.MessageService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Resource for Message operations
 * 
 * Endpoints:
 * - GET /api/v1/messages/team/{teamId} - Get all messages for a team
 * - GET /api/v1/messages/team/{teamId}/unread - Get unread messages count
 * - PUT /api/v1/messages/{messageId}/read - Mark message as read
 * - PUT /api/v1/messages/team/{teamId}/read-all - Mark all messages as read
 */
@Path("/api/v1/messages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageResource {

    @Inject
    MessageService messageService;

    @Inject
    AuthService authService;

    /**
     * Get all messages for a team
     */
    @GET
    @Path("/team/{teamId}")
    public Response getMessagesForTeam(
            @PathParam("teamId") UUID teamId,
            @HeaderParam("Authorization") String authHeader) {

        // Validate authorization
        Response authResponse = validateTeamAccess(authHeader, teamId);
        if (authResponse != null) {
            return authResponse;
        }

        List<Message> messages = messageService.findByTeamId(teamId);
        return Response.ok(messages.stream().map(this::toDto).toList()).build();
    }

    /**
     * Get unread messages count for a team
     */
    @GET
    @Path("/team/{teamId}/unread")
    public Response getUnreadCount(
            @PathParam("teamId") UUID teamId,
            @HeaderParam("Authorization") String authHeader) {

        Response authResponse = validateTeamAccess(authHeader, teamId);
        if (authResponse != null) {
            return authResponse;
        }

        int count = messageService.countUnreadByTeamId(teamId);
        return Response.ok(new UnreadCountDto(count)).build();
    }

    /**
     * Mark a message as read
     */
    @PUT
    @Path("/{messageId}/read")
    public Response markAsRead(
            @PathParam("messageId") UUID messageId,
            @HeaderParam("Authorization") String authHeader) {

        String token = extractToken(authHeader);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Missing or invalid authorization token"))
                .build();
        }

        Optional<UUID> authTeamId = authService.validateAccessToken(token);
        if (authTeamId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Invalid or expired access token"))
                .build();
        }

        // Verify message belongs to authenticated team
        Optional<Message> messageOpt = messageService.findById(messageId);
        if (messageOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Message not found"))
                .build();
        }

        if (!messageOpt.get().getTeamId().equals(authTeamId.get())) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Cannot access messages of another team"))
                .build();
        }

        messageService.markAsRead(messageId);
        return Response.ok(new SuccessResponse(true)).build();
    }

    /**
     * Mark all messages as read for a team
     */
    @PUT
    @Path("/team/{teamId}/read-all")
    public Response markAllAsRead(
            @PathParam("teamId") UUID teamId,
            @HeaderParam("Authorization") String authHeader) {

        Response authResponse = validateTeamAccess(authHeader, teamId);
        if (authResponse != null) {
            return authResponse;
        }

        messageService.markAllAsReadForTeam(teamId);
        return Response.ok(new SuccessResponse(true)).build();
    }

    // Helper methods

    private Response validateTeamAccess(String authHeader, UUID teamId) {
        String token = extractToken(authHeader);
        if (token == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Missing or invalid authorization token"))
                .build();
        }

        Optional<UUID> authTeamId = authService.validateAccessToken(token);
        if (authTeamId.isEmpty()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse("Invalid or expired access token"))
                .build();
        }

        if (!authTeamId.get().equals(teamId)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorResponse("Cannot access messages of another team"))
                .build();
        }

        return null; // Access granted
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private MessageDto toDto(Message message) {
        return new MessageDto(
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

    // DTOs
    public record MessageDto(
        UUID id,
        UUID teamId,
        String messageType,
        String content,
        String icon,
        String sentAt,
        boolean delivered,
        boolean read
    ) {}

    public record UnreadCountDto(int count) {}

    public record SuccessResponse(boolean success) {}

    public record ErrorResponse(String message) {}
}
