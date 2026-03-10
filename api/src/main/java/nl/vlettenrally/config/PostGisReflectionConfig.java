package nl.vlettenrally.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers PostGIS JDBC classes for reflection in native image builds.
 * This ensures PostGIS type handlers are available when PostgreSQL initializes JDBC types.
 */
@RegisterForReflection(
    targets = {
        // PostGIS geometry types
        net.postgis.jdbc.PGbox3d.class,
        net.postgis.jdbc.PGbox2d.class,
        net.postgis.jdbc.PGlseg.class,
        net.postgis.jdbc.PGpath.class,
        net.postgis.jdbc.PGpoint.class,
        net.postgis.jdbc.PGpolygon.class,
        net.postgis.jdbc.PGcircle.class,

        // Additional PostGIS JDBC classes if needed
        net.postgis.jdbc.PGgeometry.class,
        net.postgis.jdbc.geometry.Geometry.class,
        net.postgis.jdbc.geometry.Point.class,
        net.postgis.jdbc.geometry.Polygon.class,
        net.postgis.jdbc.geometry.LineString.class,
        net.postgis.jdbc.geometry.MultiPoint.class,
        net.postgis.jdbc.geometry.MultiLineString.class,
        net.postgis.jdbc.geometry.MultiPolygon.class,
        net.postgis.jdbc.geometry.GeometryCollection.class
    },
    classNames = {
        // Register by class name in case some types are optional or dynamic
        "net.postgis.jdbc.PGbox3d",
        "net.postgis.jdbc.PGbox2d",
        "net.postgis.jdbc.PGlseg",
        "net.postgis.jdbc.PGpath",
        "net.postgis.jdbc.PGpoint",
        "net.postgis.jdbc.PGpolygon",
        "net.postgis.jdbc.PGcircle",
        "net.postgis.jdbc.PGgeometry",
        "net.postgis.jdbc.geometry.Geometry",
        "net.postgis.jdbc.geometry.Point",
        "net.postgis.jdbc.geometry.Polygon",
        "net.postgis.jdbc.geometry.LineString",
        "net.postgis.jdbc.geometry.MultiPoint",
        "net.postgis.jdbc.geometry.MultiLineString",
        "net.postgis.jdbc.geometry.MultiPolygon",
        "net.postgis.jdbc.geometry.GeometryCollection"
    }
)
public class PostGisReflectionConfig {
    // This class is purely for GraalVM native image configuration via annotations
}

