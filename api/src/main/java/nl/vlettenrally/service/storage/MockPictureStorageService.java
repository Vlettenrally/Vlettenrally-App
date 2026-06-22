package nl.vlettenrally.service.storage;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    public void delete(String objectKey) {
        // No-op in mock mode.
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
