package nl.vlettenrally.service.util;

import org.locationtech.jts.geom.Point;

/**
 * Geodetic distance helpers. Uses the Haversine formula, which approximates
 * PostGIS {@code ST_Distance(geography, geography)} for the short consecutive-fix
 * segments we sum when computing travelled distance.
 */
public final class GeoDistance {

    /**
     * Mean Earth radius in meters (WGS-84).
     */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoDistance() {}

    /**
     * Haversine distance in meters between two WGS-84 longitude/latitude pairs (degrees).
     */
    public static double haversineMeters(double lon1, double lat1, double lon2, double lat2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double dLat = lat2Rad - lat1Rad;
        double dLon = Math.toRadians(lon2 - lon1);

        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }

    /**
     * Haversine distance in meters between two JTS Points (X = longitude, Y = latitude, degrees).
     */
    public static double haversineMeters(Point a, Point b) {
        return haversineMeters(a.getX(), a.getY(), b.getX(), b.getY());
    }

    /**
     * Initial bearing (forward azimuth) in degrees from point 1 to point 2, normalized to
     * [0, 360) where 0 = north, 90 = east. Inputs are WGS-84 longitude/latitude in degrees.
     */
    public static double bearingDegrees(double lon1, double lat1, double lon2, double lat2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                 - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    /**
     * Initial bearing in degrees between two JTS Points (X = longitude, Y = latitude, degrees).
     */
    public static double bearingDegrees(Point a, Point b) {
        return bearingDegrees(a.getX(), a.getY(), b.getX(), b.getY());
    }

    /**
     * Smallest absolute difference between two bearings (degrees), in [0, 180].
     * 0 means same direction, 180 means an exact reversal.
     */
    public static double turnAngleDegrees(double bearing1, double bearing2) {
        double diff = Math.abs(bearing1 - bearing2) % 360.0;
        return diff > 180.0 ? 360.0 - diff : diff;
    }
}
