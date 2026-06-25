package nl.vlettenrally.api.v1;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import nl.vlettenrally.api.security.SecurityRoles;
import nl.vlettenrally.data.converter.PointEntryConverter;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.dto.common.ErrorResponse;
import nl.vlettenrally.data.dto.points.CreateAdjustmentRequest;
import nl.vlettenrally.data.dto.points.EditPointEntryRequest;
import nl.vlettenrally.data.dto.points.PointEntryListResponse;
import nl.vlettenrally.data.enums.PointEntryType;
import nl.vlettenrally.data.repository.PointEntryRepository;
import nl.vlettenrally.service.PointsLedgerService;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for the points ledger.
 * <p>
 * - GET /api/v1/admin/point-entries?gameId=&teamId=&entryType=&page=&pageSize= — paginated list
 * - POST /api/v1/admin/point-entries                                          — manual adjustment
 * - PATCH /api/v1/admin/point-entries/{id}                                    — edit amount
 */
@Path("/api/v1/admin/point-entries")
@RolesAllowed({SecurityRoles.ADMIN, SecurityRoles.CHECKPOINT_MANAGER})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PointEntryResource {

    @Inject
    PointEntryRepository pointEntryRepository;

    @Inject
    PointsLedgerService pointsLedgerService;

    @Inject
    PointEntryConverter pointEntryConverter;

    @GET
    public Response list(
            @QueryParam("gameId") UUID gameId,
            @QueryParam("teamId") UUID teamId,
            @QueryParam("entryType") String entryTypeRaw,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("25") int pageSize) {

        if (gameId == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("gameId is required")).build();
        }
        if (pageSize < 1 || pageSize > 200) pageSize = 25;
        if (page < 0) page = 0;

        PointEntryType entryType = null;
        if (entryTypeRaw != null && !entryTypeRaw.isBlank()) {
            try {
                entryType = PointEntryType.valueOf(entryTypeRaw);
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Unknown entryType: " + entryTypeRaw)).build();
            }
        }

        List<PointEntry> entries = pointEntryRepository.findByGame(gameId, teamId, entryType, page, pageSize);
        long total = pointEntryRepository.countByGame(gameId, teamId, entryType);
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return Response.ok(new PointEntryListResponse(
            entries.stream().map(pointEntryConverter::toResponse).toList(),
            page,
            pageSize,
            total,
            totalPages
        )).build();
    }

    @POST
    public Response createAdjustment(CreateAdjustmentRequest request, @Context SecurityContext ctx) {
        if (request == null || request.teamId() == null || request.gameId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("teamId and gameId are required")).build();
        }
        try {
            PointEntry entry = pointsLedgerService.createEntry(
                request.teamId(),
                request.gameId(),
                PointEntryType.MANUAL_ADJUSTMENT,
                request.amount(),
                null,
                request.description(),
                null,
                callerId(ctx)
            );
            return Response.status(Response.Status.CREATED)
                .entity(pointEntryConverter.toResponse(entry)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}")
    public Response editEntry(
            @PathParam("id") UUID id,
            EditPointEntryRequest request,
            @Context SecurityContext ctx) {

        if (request == null || request.reason() == null || request.reason().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse("reason is required")).build();
        }
        try {
            PointEntry entry = pointsLedgerService.editEntry(id, request.amount(), request.reason(), callerId(ctx));
            return Response.ok(pointEntryConverter.toResponse(entry)).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(e.getMessage())).build();
        } catch (BadRequestException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(e.getMessage())).build();
        }
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
