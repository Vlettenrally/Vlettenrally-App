package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.converter.AutomationConverter;
import nl.vlettenrally.data.domain.AutomationExecution;
import nl.vlettenrally.data.domain.AutomationRule;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.data.dto.automation.AutomationExecutionListResponse;
import nl.vlettenrally.data.dto.automation.AutomationExecutionResponse;
import nl.vlettenrally.data.dto.automation.AutomationRuleRequest;
import nl.vlettenrally.data.dto.automation.AutomationRuleResponse;
import nl.vlettenrally.data.dto.automation.CopyAutomationsRequest;
import nl.vlettenrally.data.dto.automation.SetEnabledRequest;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.repository.TeamRepository;
import nl.vlettenrally.service.AutomationService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin CRUD for a game's automation rules plus their execution log.
 */
@Path("/api/v1/games/{gameId}/automations")
@RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.GAME_MASTER})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AutomationResource {

    @Inject
    AutomationService automationService;

    @Inject
    AutomationConverter automationConverter;

    @Inject
    TeamRepository teamRepository;

    @GET
    public Response listRules(@PathParam("gameId") UUID gameId) {
        List<AutomationRule> rules = automationService.listRules(gameId);
        Map<UUID, Instant> lastFired = automationService.lastFiredAt(
            rules.stream().map(AutomationRule::getId).collect(Collectors.toSet()));

        List<AutomationRuleResponse> response = rules.stream()
            .map(rule -> automationConverter.toResponse(rule,
                lastFired.get(rule.getId()),
                automationService.executionCount(rule.getId())))
            .toList();
        return Response.ok(response).build();
    }

    @POST
    public Response createRule(@PathParam("gameId") UUID gameId, AutomationRuleRequest request,
                               @Context SecurityContext ctx) {
        try {
            AutomationRule created = automationService.createRule(
                gameId, automationConverter.toDomain(request), callerId(ctx));
            return Response.status(Response.Status.CREATED)
                .entity(automationConverter.toResponse(created, null, 0)).build();
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    @POST
    @Path("/copy")
    public Response copyRules(@PathParam("gameId") UUID gameId, CopyAutomationsRequest request,
                             @Context SecurityContext ctx) {
        if (request == null || request.sourceGameId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("sourceGameId is required")).build();
        }
        try {
            return Response.ok(
                automationService.copyRules(gameId, request.sourceGameId(), callerId(ctx))).build();
        } catch (NotFoundException e) {
            return notFound(e);
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    @GET
    @Path("/{ruleId}")
    public Response getRule(@PathParam("gameId") UUID gameId, @PathParam("ruleId") UUID ruleId) {
        try {
            AutomationRule rule = automationService.getRule(gameId, ruleId);
            return Response.ok(automationConverter.toResponse(rule,
                automationService.lastFiredAt(Set.of(ruleId)).get(ruleId),
                automationService.executionCount(ruleId))).build();
        } catch (NotFoundException e) {
            return notFound(e);
        }
    }

    @PUT
    @Path("/{ruleId}")
    public Response updateRule(@PathParam("gameId") UUID gameId, @PathParam("ruleId") UUID ruleId,
                               AutomationRuleRequest request) {
        try {
            AutomationRule updated = automationService.updateRule(
                gameId, ruleId, automationConverter.toDomain(request));
            return Response.ok(automationConverter.toResponse(updated,
                automationService.lastFiredAt(Set.of(ruleId)).get(ruleId),
                automationService.executionCount(ruleId))).build();
        } catch (NotFoundException e) {
            return notFound(e);
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    @DELETE
    @Path("/{ruleId}")
    public Response deleteRule(@PathParam("gameId") UUID gameId, @PathParam("ruleId") UUID ruleId) {
        try {
            automationService.deleteRule(gameId, ruleId);
            return Response.noContent().build();
        } catch (NotFoundException e) {
            return notFound(e);
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    @PATCH
    @Path("/{ruleId}/enabled")
    public Response setEnabled(@PathParam("gameId") UUID gameId, @PathParam("ruleId") UUID ruleId,
                               SetEnabledRequest request) {
        try {
            AutomationRule updated = automationService.setEnabled(gameId, ruleId, request.enabled());
            return Response.ok(automationConverter.toResponse(updated,
                automationService.lastFiredAt(Set.of(ruleId)).get(ruleId),
                automationService.executionCount(ruleId))).build();
        } catch (NotFoundException e) {
            return notFound(e);
        } catch (BadRequestException e) {
            return badRequest(e);
        }
    }

    @GET
    @Path("/{ruleId}/executions")
    public Response listExecutions(@PathParam("gameId") UUID gameId, @PathParam("ruleId") UUID ruleId,
                                   @QueryParam("page") @DefaultValue("0") int page,
                                   @QueryParam("pageSize") @DefaultValue("25") int pageSize) {
        if (pageSize < 1 || pageSize > 200) pageSize = 25;
        if (page < 0) page = 0;
        try {
            List<AutomationExecution> executions = automationService.listExecutions(gameId, ruleId, page, pageSize);
            long total = automationService.countExecutions(gameId, ruleId);

            Map<UUID, String> teamNames = teamRepository.findByGameId(gameId).stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));
            List<AutomationExecutionResponse> items = executions.stream()
                .map(execution -> automationConverter.toExecutionResponse(execution,
                    execution.getTeamId() != null ? teamNames.get(execution.getTeamId()) : null))
                .toList();

            return Response.ok(new AutomationExecutionListResponse(
                items, page, pageSize, total, (int) Math.ceil((double) total / pageSize))).build();
        } catch (NotFoundException e) {
            return notFound(e);
        }
    }

    private static Response badRequest(Exception e) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ErrorResponse(e.getMessage())).build();
    }

    private static Response notFound(Exception e) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new ErrorResponse(e.getMessage())).build();
    }

    private static UUID callerId(SecurityContext ctx) {
        if (ctx == null || ctx.getUserPrincipal() == null) return null;
        try {
            return UUID.fromString(ctx.getUserPrincipal().getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
