package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.client.PreRegistrationApiClient;
import nl.vlettenrally.client.dto.ExternalEvent;
import nl.vlettenrally.client.exception.PreRegistrationApiException;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.external.ConnectionTestResponse;
import nl.vlettenrally.data.dto.external.ExternalEventResponse;

import java.util.List;

/**
 * REST Resource for external API integration
 * 
 * Endpoints:
 * - GET /api/v1/external/events - Get available events from pre-registration system
 */
@Path("/api/v1/external")
@RolesAllowed("admin")
@Produces(MediaType.APPLICATION_JSON)
public class ExternalApiResource {

    @Inject
    PreRegistrationApiClient preRegistrationClient;

    /**
     * Get available events from the pre-registration API
     */
    @GET
    @Path("/events")
    public Response getExternalEvents() {
        try {
            List<ExternalEvent> events = preRegistrationClient.fetchEvents();
            
            // Map to response DTOs
            List<ExternalEventResponse> response = events.stream()
                    .map(e -> new ExternalEventResponse(
                            e.getEventId(),
                            e.getEventName(),
                            e.getBookingCount()
                    ))
                    .toList();
            
            return Response.ok(response).build();
            
        } catch (PreRegistrationApiException e) {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(new ErrorResponse("Failed to fetch external events: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Test connection to the pre-registration API
     */
    @GET
    @Path("/test")
    public Response testConnection() {
        boolean connected = preRegistrationClient.testConnection();
        
        if (connected) {
            return Response.ok(new ConnectionTestResponse(true, "Connection successful")).build();
        } else {
            return Response.status(Response.Status.BAD_GATEWAY)
                    .entity(new ConnectionTestResponse(false, "Connection failed"))
                    .build();
        }
    }
}
