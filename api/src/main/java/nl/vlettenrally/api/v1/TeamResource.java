package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.websocket.server.PathParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.api.security.TeamScoped;
import nl.vlettenrally.api.security.TeamScopedOrAdmin;
import nl.vlettenrally.data.converter.TeamConverter;
import nl.vlettenrally.data.domain.DeviceRegistration;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.auth.CheckInStateResponse;
import nl.vlettenrally.data.dto.auth.DeviceResponse;
import nl.vlettenrally.data.dto.auth.SetMainDeviceRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.team.EmergencyPhoneUpdateRequest;
import nl.vlettenrally.data.dto.team.ParticipantsUpdateRequest;
import nl.vlettenrally.data.dto.team.RivalsUpdateRequest;
import nl.vlettenrally.data.dto.team.TeamCreateRequest;
import nl.vlettenrally.data.dto.team.TeamUpdateRequest;
import nl.vlettenrally.data.dto.team.AssignTrackerRequest;
import nl.vlettenrally.data.repository.DeviceRegistrationRepository;
import nl.vlettenrally.service.CheckInCacheService;
import nl.vlettenrally.service.CheckInCoordinationService;
import nl.vlettenrally.service.TeamService;
import nl.vlettenrally.service.TrackerAssignmentService;
import nl.vlettenrally.websockets.WebSocketEventTypes;
import nl.vlettenrally.websockets.WebSocketPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Resource for Team operations
 * <p>
 * Endpoints:
 * - GET /api/v1/teams - Get all teams
 * - GET /api/v1/teams/{teamId} - Get team by ID
 * - GET /api/v1/teams/{teamId}/rival - Get rival team
 * - GET /api/v1/team/me - Get current authenticated team
 * - POST /api/v1/teams/{teamId}/check-in - Check in a team
 * - PUT /api/v1/teams/{teamId}/emergency-phones - Update emergency phone numbers
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(SecurityRoles.STAFF)
public class TeamResource {

    @Inject
    TeamService teamService;

    @Inject
    TeamConverter teamConverter;

    @Inject
    WebSocketPublisher webSocketPublisher;

    @Inject
    CheckInCoordinationService coordinationService;

    @Inject
    DeviceRegistrationRepository deviceRegistrationRepository;

    @Inject
    TrackerAssignmentService trackerAssignmentService;

    @Inject
    CheckInCacheService checkInCacheService;

    /**
     * Get all teams
     */
    @GET
    @Path("/teams")
    public Response getAllTeams() {
        List<Team> teams = teamService.findAll();
        return Response.ok(teams.stream().map(teamConverter::toDto).toList()).build();
    }

    /**
     * Get team by ID
     */
    @GET
    @Path("/teams/{teamId}")
    public Response getTeamById(@PathParam("teamId") UUID teamId) {
        return teamService.findById(teamId)
            .map(team -> Response.ok(teamConverter.toDto(team)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build());
    }

    /**
     * Get rival teams for a team
     */
    @GET
    @RolesAllowed({SecurityRoles.STAFF, SecurityRoles.TEAM})
    @TeamScopedOrAdmin
    @Path("/teams/{teamId}/rivals")
    public Response getRivalTeams(@PathParam("teamId") UUID teamId) {
        List<Team> rivals = teamService.findRivalTeams(teamId);
        return Response.ok(rivals.stream().map(teamConverter::toPublicResponse).toList()).build();
    }

    /**
     * Replace the team's rival list. Up to 5 rivals from the same game; never self.
     * Locked once the game is COMPLETED.
     */
    @PUT
    @RolesAllowed({"team"})
    @TeamScoped
    @Path("/teams/{teamId}/rivals")
    public Response setRivals(@PathParam("teamId") UUID teamId, RivalsUpdateRequest request) {
        if (request == null || request.rivalTeamIds() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("rivalTeamIds is required"))
                .build();
        }
        try {
            List<Team> rivals = teamService.setRivals(teamId, request.rivalTeamIds());
            return Response.ok(rivals.stream().map(teamConverter::toPublicResponse).toList()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Get current authenticated team's data
     * Requires authentication via access token
     */
    @GET
    @Path("/team/me")
    @RolesAllowed({ "team" })
    public Response getMyTeam(@Context SecurityContext securityContext) {
        UUID teamId = UUID.fromString(securityContext.getUserPrincipal().getName());

        return teamService.findById(teamId)
            .map(team -> Response.ok(teamConverter.toPlayerResponse(team)).build())
            .orElse(Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build());
    }

    /**
     * Check in a team
     */
    @POST
    @RolesAllowed({"team"})
    @TeamScoped
    @Path("/teams/{teamId}/check-in")
    public Response checkInTeam(
            @PathParam("teamId") UUID teamId) {

        try {
            Team team = teamService.checkIn(teamId);
            return Response.ok(teamConverter.toPlayerResponse(team)).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Update emergency phone numbers for a team
     */
    @PUT
    @Path("/teams/{teamId}/emergency-phones")
    @RolesAllowed({"team"})
    @TeamScoped
    public Response updateEmergencyPhoneNumbers(
            @PathParam("teamId") UUID teamId,
            EmergencyPhoneUpdateRequest request) {

        try {
            Optional<Team> teamOpt = teamService.findById(teamId);
            if (teamOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Team not found"))
                    .build();
            }

            Team team = teamOpt.get();
            team.setEmergencyPhoneNumbers(request.phoneNumbers());
            Team updatedTeam = teamService.save(team);

            return Response.ok(teamConverter.toPlayerResponse(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Update participants for a team
     */
    @PUT
    @Path("/teams/{teamId}/participants")
    @RolesAllowed({"team"})
    @TeamScoped
    public Response updateParticipants(
            @PathParam("teamId") UUID teamId,
            ParticipantsUpdateRequest request) {

        try {
            Optional<Team> teamOpt = teamService.findById(teamId);
            if (teamOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Team not found"))
                    .build();
            }

            Team team = teamOpt.get();
            team.setParticipants(request.participants());
            Team updatedTeam = teamService.save(team);

            return Response.ok(teamConverter.toPlayerResponse(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Update an existing team
     */
    @PUT
    @Path("/teams/{teamId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response updateTeam(
            @PathParam("teamId") UUID teamId,
            TeamUpdateRequest request) {
        
        try {
            Optional<Team> teamOpt = teamService.findById(teamId);
            if (teamOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Team not found"))
                    .build();
            }

            Team team = teamOpt.get();
            
            // Update team fields
            if (request.name() != null) {
                team.setName(request.name());
            }
            if (request.description() != null) {
                team.setDescription(request.description());
            }
            if (request.participants() != null) {
                team.setParticipants(request.participants());
            }
            if (request.emergencyPhoneNumbers() != null) {
                team.setEmergencyPhoneNumbers(request.emergencyPhoneNumbers());
            }
            if (request.notes() != null) {
                team.setNotes(request.notes());
            }

            // Points are no longer mutated via this endpoint — use PointEntryResource (/api/v1/admin/point-entries)
            // so every change is recorded in the ledger.

            // Handle check-in status updates with reset logic
            // If checkedIn is explicitly set to false, also reset checkedInDigitally
            if (request.checkedIn() != null) {
                team.setCheckedIn(request.checkedIn());
                if (!request.checkedIn()) {
                    // Reset digital check-in when unchecking
                    team.setCheckedInDigitally(false);
                }
                checkInCacheService.invalidate(team.getGameId());
            }

            // Allow explicit setting of checkedInDigitally (admin override)
            // Only if checkedIn wasn't just set to false
            if (request.checkedInDigitally() != null &&
                (request.checkedIn() == null || request.checkedIn())) {
                team.setCheckedInDigitally(request.checkedInDigitally());
            }

            // Toggle Traccar tracking. The identifier itself is generated lazily
            // by TeamService.save() when enabling, and kept when disabling.
            if (request.traccarEnabled() != null) {
                team.setTraccarEnabled(request.traccarEnabled());
            }

            // Admin override for the finished state (set/reverse a finish). Stamp
            // finishedAt on transition to finished; clear it when un-finishing.
            boolean finishedChanged = false;
            if (request.finished() != null && request.finished() != team.isFinished()) {
                team.setFinished(request.finished());
                team.setFinishedAt(request.finished() ? java.time.Instant.now() : null);
                finishedChanged = true;
            }

            Team updatedTeam = teamService.save(team);

            // Broadcast update via WebSocket
            webSocketPublisher.broadcastToGame(
                updatedTeam.getGameId(),
                "team:updated",
                teamConverter.toDto(updatedTeam)
            );

            // Push a finish toggle to the team's own dashboard live (scoped to it).
            if (finishedChanged) {
                webSocketPublisher.sendToTeam(updatedTeam.getGameId(), updatedTeam.getId(),
                    WebSocketEventTypes.TEAM_UPDATE, Map.of("finished", updatedTeam.isFinished()));
            }

            return Response.ok(teamConverter.toDto(updatedTeam)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Admin: assign a tracker from the game's pool to a team, or clear it (blank
     * trackerId). Broadcasts the update so admin dashboards refresh live.
     */
    @PUT
    @Path("/teams/{teamId}/tracker")
    public Response assignTracker(@PathParam("teamId") UUID teamId, AssignTrackerRequest request) {
        if (teamService.findById(teamId).isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build();
        }
        try {
            Team updatedTeam = trackerAssignmentService.assignToTeam(
                teamId, request != null ? request.trackerId() : null);

            webSocketPublisher.broadcastToGame(
                updatedTeam.getGameId(),
                "team:updated",
                teamConverter.toDto(updatedTeam)
            );

            return Response.ok(teamConverter.toDto(updatedTeam)).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Create a new team
     */
    @POST
    @Path("/teams")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response createTeam(TeamCreateRequest request) {
        try {
            // Validate required fields
            if (request.gameId() == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Game ID is required"))
                    .build();
            }
            if (request.name() == null || request.name().trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Team name is required"))
                    .build();
            }

            Team team = new Team();
            team.setGameId(request.gameId());
            team.setName(request.name());
            team.setDescription(request.description() != null ? request.description() : "");
            team.setParticipants(request.participants() != null ? request.participants() : new ArrayList<>());
            team.setEmergencyPhoneNumbers(request.emergencyPhoneNumbers() != null ? request.emergencyPhoneNumbers() : new ArrayList<>());
            team.setNotes(request.notes());

            Team savedTeam = teamService.save(team);
            
            // Broadcast creation via WebSocket
            webSocketPublisher.broadcastToGame(
                savedTeam.getGameId(), 
                "team:created", 
                teamConverter.toDto(savedTeam)
            );
            
            return Response.status(Response.Status.CREATED)
                .entity(teamConverter.toDto(savedTeam))
                .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Delete a team. Cascades (DB-level) to its participants, emergency phones, rivals,
     * devices, achievements, point entries, messages, location data and pictures.
     */
    @DELETE
    @Path("/teams/{teamId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response deleteTeam(@PathParam("teamId") UUID teamId) {
        Optional<Team> teamOpt = teamService.findById(teamId);
        if (teamOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build();
        }

        UUID gameId = teamOpt.get().getGameId();
        teamService.delete(teamId);

        // Broadcast removal so admin dashboards drop the team live.
        if (gameId != null) {
            webSocketPublisher.broadcastToGame(gameId, "team:deleted", Map.of("id", teamId.toString()));
        }

        return Response.noContent().build();
    }

    /**
     * Admin: list every registered check-in device for a team.
     */
    @GET
    @Path("/teams/{teamId}/devices")
    public Response listTeamDevices(@PathParam("teamId") UUID teamId) {
        Optional<Team> teamOpt = teamService.findById(teamId);
        if (teamOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse("Team not found"))
                .build();
        }
        UUID mainDeviceId = teamOpt.get().getMainDeviceId();
        List<DeviceResponse> devices = deviceRegistrationRepository.findAllByTeamId(teamId).stream()
            .map((DeviceRegistration d) -> new DeviceResponse(
                d.getId(),
                d.getNickname(),
                d.getId().equals(mainDeviceId),
                d.getCreatedAt(),
                d.getLastSeen()
            ))
            .toList();
        return Response.ok(devices).build();
    }

    /**
     * Admin: hand main-device status to a different registered device.
     */
    @PUT
    @Path("/teams/{teamId}/devices/main")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response setMainDevice(@PathParam("teamId") UUID teamId, SetMainDeviceRequest request) {
        if (request == null || request.deviceId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("deviceId is required"))
                .build();
        }
        try {
            coordinationService.setMainDevice(teamId, request.deviceId());
            CheckInStateResponse state = coordinationService.getState(teamId);
            return Response.ok(state).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Admin: reset check-in for a team (deletes every device, clears participants +
     * emergency phone numbers + checked-in flags, revokes refresh tokens).
     */
    @DELETE
    @Path("/teams/{teamId}/devices")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response resetTeamDevices(@PathParam("teamId") UUID teamId) {
        try {
            coordinationService.resetTeam(teamId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }

    /**
     * Admin: remove a single device from a team. Revokes that device's access tokens
     * and demotes main-device status to the next-oldest device when needed.
     */
    @DELETE
    @Path("/teams/{teamId}/devices/{deviceId}")
    @RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER, SecurityRoles.RALLY_DESK})
    public Response removeTeamDevice(@PathParam("teamId") UUID teamId, @PathParam("deviceId") UUID deviceId) {
        try {
            coordinationService.removeDevice(teamId, deviceId);
            return Response.noContent().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage()))
                .build();
        }
    }
}