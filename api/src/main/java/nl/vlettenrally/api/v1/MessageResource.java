package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.websocket.server.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.security.TeamScoped;
import nl.vlettenrally.data.domain.Message;
import nl.vlettenrally.data.dto.common.SuccessResponse;
import nl.vlettenrally.data.dto.message.MessageResponse;
import nl.vlettenrally.data.dto.message.UnreadCountResponse;
import nl.vlettenrally.service.MessageService;

import java.util.List;
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
@RolesAllowed({"team", "admin"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageResource {

    @Inject
    MessageService messageService;

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
}
