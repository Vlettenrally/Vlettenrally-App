package nl.vlettenrally.api.v1;

import io.quarkus.logging.Log;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import nl.vlettenrally.data.domain.LocationData;
import nl.vlettenrally.data.domain.Team;
import nl.vlettenrally.service.LocationIngestService;
import nl.vlettenrally.service.TeamService;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Traccar/OsmAnd-compatible location ingestion endpoint.
 * <p>
 * The Traccar Client mobile app on a team's main device is configured during
 * check-in with this URL and the team's {@code traccarIdentifier} as its device
 * id. It then reports GPS here directly using the OsmAnd protocol — parameters in
 * the query string, on both GET and POST.
 * <p>
 * The identifier is the only credential, so the endpoint is public. Unknown or
 * disabled identifiers and points rejected by the ingestion guards still return
 * 200 so the app does not retry-storm; only the write is skipped.
 */
@Path("/api/v1/traccar")
public class TraccarResource {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double KNOTS_TO_KMH = 1.852;

    @Inject
    TeamService teamService;

    @Inject
    LocationIngestService locationIngestService;

    @POST
    @PermitAll
    public Response reportPost(
            @QueryParam("id") String id,
            @QueryParam("lat") Double lat,
            @QueryParam("lon") Double lon,
            @QueryParam("timestamp") String timestamp,
            @QueryParam("altitude") Double altitude,
            @QueryParam("speed") Double speed,
            @QueryParam("bearing") Double bearing,
            @QueryParam("accuracy") Double accuracy,
            @QueryParam("hdop") Double hdop) {
        return report(id, lat, lon, timestamp, altitude, speed, bearing, accuracy, hdop);
    }

    @GET
    @PermitAll
    public Response reportGet(
            @QueryParam("id") String id,
            @QueryParam("lat") Double lat,
            @QueryParam("lon") Double lon,
            @QueryParam("timestamp") String timestamp,
            @QueryParam("altitude") Double altitude,
            @QueryParam("speed") Double speed,
            @QueryParam("bearing") Double bearing,
            @QueryParam("accuracy") Double accuracy,
            @QueryParam("hdop") Double hdop) {
        return report(id, lat, lon, timestamp, altitude, speed, bearing, accuracy, hdop);
    }

    private Response report(String id, Double lat, Double lon, String timestamp,
                            Double altitude, Double speed, Double bearing, Double accuracy, Double hdop) {
        if (id == null || id.isBlank() || lat == null || lon == null) {
            // Missing required params — still 200 to keep the tracker quiet.
            Log.debugf("Traccar report ignored: missing required params (id=%s, lat=%s, lon=%s)", id, lat, lon);
            return Response.ok().build();
        }

        Optional<Team> teamOpt = teamService.findByTraccarIdentifier(id);
        if (teamOpt.isEmpty()) {
            Log.debugf("Traccar report ignored: no team matches identifier %s", id);
            return Response.ok().build();
        }
        if (!teamOpt.get().isTraccarEnabled()) {
            Log.debugf("Traccar report ignored: tracking disabled for team %s (id %s)",
                teamOpt.get().getId(), id);
            return Response.ok().build();
        }
        Team team = teamOpt.get();

        LocationData reading = new LocationData();
        reading.setTeamId(team.getId());
        reading.setGameId(team.getGameId());
        reading.setLocation(point(lat, lon));
        reading.setLocationTime(parseTimestamp(timestamp));
        // OsmAnd reports accuracy in meters; fall back to hdop when absent.
        reading.setAccuracy(toBigDecimal(accuracy != null ? accuracy : hdop));
        reading.setAltitude(toBigDecimal(altitude));
        // OsmAnd speed is in knots; we store km/h.
        reading.setSpeed(speed != null ? BigDecimal.valueOf(speed * KNOTS_TO_KMH) : null);
        reading.setHeading(toBigDecimal(bearing));

        // Broadcast the accepted fix to admin map subscribers; the poller batches a
        // whole sweep, but a single Traccar report is its own one-element batch.
        locationIngestService.ingest(reading, team.getName())
            .ifPresent(location -> locationIngestService.broadcastToMap(team.getGameId(), List.of(location)));
        return Response.ok().build();
    }

    private static Point point(double lat, double lon) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
    }

    private static BigDecimal toBigDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    /**
     * OsmAnd sends {@code timestamp} as either Unix epoch seconds or ISO-8601.
     * Falls back to now when absent or unparseable.
     */
    private static Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(raw.trim()));
        } catch (NumberFormatException ignored) {
            // not epoch seconds
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception ignored) {
            return Instant.now();
        }
    }
}
