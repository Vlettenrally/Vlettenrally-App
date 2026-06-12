package nl.vlettenrally.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Traccar/OsmAnd-compatible location ingestion endpoint.
 * <p>
 * The Traccar Client mobile app on a team's main device is configured during
 * check-in with this URL and the team's {@code traccarIdentifier} as its device
 * id. It then reports GPS here directly using the OsmAnd protocol. The values can
 * arrive as query-string parameters (typically GET), as a {@code key=value&...}
 * request body (the official Traccar Client's POST), or as a JSON object. The
 * client does not reliably set a matching {@code Content-Type}, so rather than
 * lean on JAX-RS content negotiation (which 415s or silently drops the body on a
 * mismatch) we read the raw body, sniff JSON vs. form, and coalesce with any query
 * params — matching how the reference Traccar server decodes OsmAnd.
 * <p>
 * The identifier is the only credential, so the endpoint is public. Unknown or
 * disabled identifiers and points rejected by the ingestion guards still return
 * 200 so the app does not retry-storm; only the write is skipped.
 */
@Path("/api/v1/traccar")
public class TraccarResource {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final double KNOTS_TO_KMH = 1.852;

    /** OsmAnd field names we read, in either a form body or a JSON object. */
    private static final String[] FIELDS =
        {"id", "lat", "lon", "timestamp", "altitude", "speed", "bearing", "accuracy", "hdop"};

    @Inject
    TeamService teamService;

    @Inject
    LocationIngestService locationIngestService;

    @Inject
    ObjectMapper objectMapper;

    @POST
    @PermitAll
    public Response reportPost(
            String body,
            @Context HttpServerRequest httpRequest,
            @QueryParam("id") String id,
            @QueryParam("lat") Double lat,
            @QueryParam("lon") Double lon,
            @QueryParam("timestamp") String timestamp,
            @QueryParam("altitude") Double altitude,
            @QueryParam("speed") Double speed,
            @QueryParam("bearing") Double bearing,
            @QueryParam("accuracy") Double accuracy,
            @QueryParam("hdop") Double hdop) {
        logRawRequest("POST", httpRequest, body);
        // No @Consumes: the official Traccar Client doesn't reliably set a content
        // type, so we read the raw body ourselves (JSON object or key=value form)
        // and fall back to it for any value not already present in the query string.
        Map<String, String> fields = parseBody(body);
        return report(
            id != null ? id : fields.get("id"),
            lat != null ? lat : parseDouble(fields.get("lat")),
            lon != null ? lon : parseDouble(fields.get("lon")),
            timestamp != null ? timestamp : fields.get("timestamp"),
            altitude != null ? altitude : parseDouble(fields.get("altitude")),
            speed != null ? speed : parseDouble(fields.get("speed")),
            bearing != null ? bearing : parseDouble(fields.get("bearing")),
            accuracy != null ? accuracy : parseDouble(fields.get("accuracy")),
            hdop != null ? hdop : parseDouble(fields.get("hdop")));
    }

    /** Read a request body that may be a JSON object or a {@code key=value&...} form. */
    private Map<String, String> parseBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        String trimmed = body.trim();
        return trimmed.startsWith("{") ? parseJson(trimmed) : parseForm(trimmed);
    }

    private Map<String, String> parseJson(String body) {
        Map<String, String> map = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String field : FIELDS) {
                JsonNode value = root.get(field);
                if (value != null && !value.isNull()) {
                    map.put(field, value.asText());
                }
            }
        } catch (Exception e) {
            Log.debugf("Traccar report: body looked like JSON but failed to parse: %s", e.getMessage());
        }
        return map;
    }

    /** Parse a {@code key=value&key=value} body (form/OsmAnd style) into a map. */
    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @GET
    @PermitAll
    public Response reportGet(
            @Context HttpServerRequest httpRequest,
            @QueryParam("id") String id,
            @QueryParam("lat") Double lat,
            @QueryParam("lon") Double lon,
            @QueryParam("timestamp") String timestamp,
            @QueryParam("altitude") Double altitude,
            @QueryParam("speed") Double speed,
            @QueryParam("bearing") Double bearing,
            @QueryParam("accuracy") Double accuracy,
            @QueryParam("hdop") Double hdop) {
        logRawRequest("GET", httpRequest, null);
        return report(id, lat, lon, timestamp, altitude, speed, bearing, accuracy, hdop);
    }

    /**
     * Temporary diagnostic: dump exactly what the tracker sends so we can see where
     * the OsmAnd values actually live (query string, body, or stripped en route).
     * Logged at INFO so it survives a narrowed log level; remove once resolved.
     */
    private static void logRawRequest(String verb, HttpServerRequest req, String body) {
        if (req == null) {
            return;
        }
        Log.infof("Traccar %s raw request: uri=%s query=%s contentType=%s contentLength=%s userAgent=%s bodyLen=%d body=%s",
            verb,
            req.uri(),
            req.query(),
            req.getHeader("Content-Type"),
            req.getHeader("Content-Length"),
            req.getHeader("User-Agent"),
            body != null ? body.length() : 0,
            body != null ? body : "");
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
