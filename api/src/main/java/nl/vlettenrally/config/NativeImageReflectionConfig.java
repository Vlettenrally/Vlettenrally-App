package nl.vlettenrally.config;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers third-party classes for GraalVM native image reflection.
 * <p>
 * Hibernate Spatial uses geolatte-geom's {@code Wkt.newDecoder()} which instantiates
 * codec classes via {@code Class.newInstance()} (reflection). In native image mode,
 * these classes must be explicitly registered.
 */
@RegisterForReflection(targets = {
    org.geolatte.geom.codec.PostgisWktDecoder.class,
    org.geolatte.geom.codec.PostgisWktEncoder.class,
    org.geolatte.geom.codec.PostgisWkbDecoder.class,
    org.geolatte.geom.codec.PostgisWkbEncoder.class,
    org.geolatte.geom.codec.PostgisWkbV2Encoder.class,
})
public class NativeImageReflectionConfig {
}

