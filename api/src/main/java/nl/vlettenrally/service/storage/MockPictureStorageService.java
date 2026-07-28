package nl.vlettenrally.service.storage;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Mock picture storage for dev/test: returns deterministic fake URLs and synthetic
 * metadata so no Storj credentials or network access are needed.
 * Only active in dev and test profiles - replaced by S3PictureStorageService in prod.
 */
@IfBuildProfile(anyOf = {"dev", "test"})
@ApplicationScoped
public class MockPictureStorageService implements PictureStorageService {

    private static final long SYNTHETIC_SIZE_BYTES = 512_000L;

    /**
     * A valid 1x1 baseline JPEG, returned as the bytes of every object. Keeps the mock free of
     * network and image-library dependencies while still producing archives whose entries open
     * as real images.
     */
    private static final byte[] SYNTHETIC_JPEG = Base64.getDecoder().decode(
        "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a"
        + "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAA"
        + "AAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==");

    @Override
    public String presignUpload(String objectKey, String contentType, Duration ttl) {
        return "http://localhost:8080/mock-upload/" + encode(objectKey);
    }

    @Override
    public String presignView(String objectKey, Duration ttl) {
        // Picsum gives a deterministic placeholder image so the dev UI shows thumbnails.
        return "https://picsum.photos/seed/" + encode(objectKey) + "/600/400";
    }

    @Override
    public Optional<ObjectMetadata> head(String objectKey) {
        // Pretend every reserved object was uploaded successfully.
        return Optional.of(new ObjectMetadata(SYNTHETIC_SIZE_BYTES, "image/jpeg"));
    }

    @Override
    public Optional<InputStream> openObject(String objectKey) {
        // Pretend every reserved object exists, mirroring head().
        return Optional.of(new ByteArrayInputStream(SYNTHETIC_JPEG));
    }

    @Override
    public void delete(String objectKey) {
        // No-op in mock mode.
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
