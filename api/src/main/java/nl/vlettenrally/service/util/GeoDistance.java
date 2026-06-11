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
}
