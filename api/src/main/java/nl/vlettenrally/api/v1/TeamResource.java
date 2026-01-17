package nl.vlettenrally.api.v1;

import java.util.List;
import java.util.UUID;

import jakarta.annotation.Resource;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.api.v1.Dto.GameDto;
import nl.vlettenrally.api.v1.Dto.TeamDto;

@Resource
@Path("team")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TeamResource {
    
    private List<TeamDto> teams;

    public TeamResource() {
        GameDto game = new GameDto(
            "Vletten Rally 2026",
            "Annual Vletten boat rally event.",
            "2026-02-01T10:00:00Z",
            "2026-02-01T18:00:00Z"
        );

        TeamDto team1 = new TeamDto(
            game,
            "Vletten Masters",
            "Veteran team with years of experience.",
            "VL-01",
            "Blue",
            List.of("Alice", "Bob", "Eve"),
            UUID.randomUUID(),
            true,
            120,
            List.of(),
            "Prefers early start.",
            "2026-01-10T09:00:00Z",
            "2026-01-15T12:00:00Z"
        );

        TeamDto team2 = new TeamDto(
            game,
            "Speedy Scouts",
            "Young and energetic team.",
            "VL-02",
            "Red",
            List.of("Charlie", "David"),
            UUID.randomUUID(),
            false,
            95,
            List.of(team1.id()),
            "Needs assistance with check-in.",
            "2026-01-11T10:00:00Z",
            "2026-01-15T12:30:00Z"
        );
        teams = List.of(team1, team2);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<TeamDto> GetAllTeams()
    {
        return teams;
    }
    
    @GET
    @Path("{teamId}")
    @Produces(MediaType.APPLICATION_JSON)
    public TeamDto GetTeamById(@PathParam("teamId") UUID teamId)
    {
        return teams.stream().filter(team -> team.id().equals(teamId)).findFirst().orElse(null);
    }

    /**
     * Get current authenticated team's data
     * Requires authentication via access token
     * 
     * GET /api/v1/team/me
     */
    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMyTeam(@HeaderParam("Authorization") String authHeader) {
        try {
            // Extract token from Authorization header
            String token = extractToken(authHeader);
            
            if (token == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\": \"Missing or invalid authorization token\"}")
                    .build();
            }

            // TODO: Validate JWT token and extract team ID
            String teamId = validateAccessToken(token);
            
            if (teamId == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"message\": \"Invalid or expired access token\"}")
                    .build();
            }

            // TODO: Fetch team from database
            // For now, return mock data
            GameDto game = new GameDto(
                "Vletten Rally 2026",
                "Annual Vletten boat rally event.",
                "2026-02-01T10:00:00Z",
                "2026-02-01T18:00:00Z"
            );

            TeamDto team = new TeamDto(
                game,
                "Scouting Nederland",
                "Team from Scouting Nederland",
                "42",
                "blue",
                List.of("Jan", "Piet", "Klaas"),
                null,
                true,
                0,
                List.of(),
                null,
                "2026-01-16T10:00:00Z",
                "2026-01-16T10:00:00Z"
            );

            return Response.ok(team).build();
            
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"message\": \"Failed to fetch team data\"}")
                .build();
        }
    }

    // Helper methods

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String validateAccessToken(String token) {
        // TODO: Implement JWT validation using SmallRye JWT
        // For now, extract teamId from mock token
        if (token != null && token.startsWith("access_")) {
            String[] parts = token.split("_");
            if (parts.length >= 2) {
                return parts[1]; // Return teamId
            }
        }
        return null;
    }
}