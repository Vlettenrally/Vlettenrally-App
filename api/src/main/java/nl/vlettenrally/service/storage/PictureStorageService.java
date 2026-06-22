package nl.vlettenrally.service.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * Abstraction over the S3-compatible object store used for team pictures.
 * <p>
 * A real implementation talks to the Storj S3 gateway (prod); a mock implementation
 * returns deterministic fake URLs for dev/test so no credentials or network are needed.
 */
public interface PictureStorageService {

    /**
     * Generate a pre-signed URL that allows a single HTTP PUT of one object at
     * {@code objectKey} with the exact {@code contentType}. The URL is scoped to that
     * one key and expires after {@code ttl}.
     */
    String presignUpload(String objectKey, String contentType, Duration ttl);

    /** Generate a short-lived pre-signed GET URL for viewing an uploaded object. */
    String presignView(String objectKey, Duration ttl);

    /** Fetch object metadata (size + content type); empty if the object does not exist. */
    Optional<ObjectMetadata> head(String objectKey);

    /** Delete an object. No-op if it does not exist. */
    void delete(String objectKey);

    /** Metadata returned by {@link #head(String)}. */
    record ObjectMetadata(long sizeBytes, String contentType) {}
}
