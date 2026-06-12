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
 * A tracking app on a team's main device is configured during check-in with this
 * URL and the team's {@code traccarIdentifier} as its device id, then reports GPS
 * here directly. Two payload shapes are supported:
 * <ul>
 *   <li><b>OsmAnd</b> — flat {@code id/lat/lon/...} values as query-string params
 *       (typically GET) or a {@code key=value&...} body; speed in knots.</li>
 *   <li><b>Transistorsoft background-geolocation</b> — a nested JSON object with
 *       {@code device_id} and {@code location.coords}; speed in m/s.</li>
 * </ul> * <p>
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

        JsonNode json = tryParseJson(body);
        if (json != null && json.path("location").hasNonNull("coords")) {
            return reportBackgroundGeolocation(json);
        }

        Map<String, String> fields = json != null ? flatJsonFields(json) : parseForm(body);
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

    /**
     * Transistorsoft background-geolocation POSTs a nested object:
     * {@code {"device_id":..,"location":{"timestamp":<ISO-8601>,"coords":{latitude,
     * longitude,accuracy,speed,heading,altitude}}}}. Speed is in m/s (OsmAnd uses
     * knots) and a {@code -1} speed/heading means "unknown".
     */
    private Response reportBackgroundGeolocation(JsonNode root) {
        JsonNode location = root.path("location");
        JsonNode coords = location.path("coords");

        // Transistorsoft speed is m/s -> km/h; negative speed/heading mean no fix.
        Double speedMs = number(coords, "speed");
        Double speedKmh = speedMs != null && speedMs >= 0 ? speedMs * 3.6 : null;
        Double heading = number(coords, "heading");
        Double headingDeg = heading != null && heading >= 0 ? heading : null;

        return ingestFix(
            text(root, "device_id"),
            number(coords, "latitude"),
            number(coords, "longitude"),
            parseTimestamp(text(location, "timestamp")),
            number(coords, "altitude"),
            speedKmh,
            headingDeg,
            number(coords, "accuracy"));
    }

    private JsonNode tryParseJson(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception e) {
            Log.debugf("Traccar report: body looked like JSON but failed to parse: %s", e.getMessage());
            return null;
        }
    }

    /** Pull flat OsmAnd field names off a (non-nested) JSON object. */
    private static Map<String, String> flatJsonFields(JsonNode root) {
        Map<String, String> map = new HashMap<>();
        for (String field : FIELDS) {
            JsonNode value = root.get(field);
            if (value != null && !value.isNull()) {
                map.put(field, value.asText());
            }
        }
        return map;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    /** Parse a {@code key=value&key=value} body (form/OsmAnd style) into a map. */
    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) {
            return map;
        }
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
        return report(id, lat, lon, timestamp, altitude, speed, bearing, accuracy, hdop);
    }

    /** OsmAnd path: speed is in knots and accuracy falls back to hdop. */
    private Response report(String id, Double lat, Double lon, String timestamp,
                            Double altitude, Double speedKnots, Double bearing, Double accuracy, Double hdop) {
        Double speedKmh = speedKnots != null ? speedKnots * KNOTS_TO_KMH : null;
        Double acc = accuracy != null ? accuracy : hdop;
        return ingestFix(id, lat, lon, parseTimestamp(timestamp), altitude, speedKmh, bearing, acc);
    }

    /**
     * Shared sink for a single fix, with units already normalized (speed in km/h).
     * Validates, resolves the team from the identifier, persists, and broadcasts.
     */
    private Response ingestFix(String id, Double lat, Double lon, Instant time,
                               Double altitude, Double speedKmh, Double heading, Double accuracy) {
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
        reading.setLocationTime(time);
        reading.setAccuracy(toBigDecimal(accuracy));
        reading.setAltitude(toBigDecimal(altitude));
        reading.setSpeed(toBigDecimal(speedKmh));
        reading.setHeading(toBigDecimal(heading));

        // Broadcast the accepted fix to admin map subscribers; the poller batches a
        // whole sweep, but a single Traccar report is its own one-element batch.
        // detectCheckpoints=true: raw GPS has no external checkpoint feed, so passes
        // are derived here via local geofencing.
        locationIngestService.ingest(reading, team.getName(), true)
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
