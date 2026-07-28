package nl.vlettenrally.service.storage;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * Production picture storage backed by the Storj S3 gateway.
 * Only active in prod profile - replaced by MockPictureStorageService in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class S3PictureStorageService implements PictureStorageService {

    /** Valkey key prefix for cached view URLs, keyed by object key. */
    private static final String VIEW_URL_KEY_PREFIX = "pic:view-url:";

    @Inject
    S3Presigner presigner;

    @Inject
    S3Client s3Client;

    /**
     * View URLs are cached cross-instance in the same Valkey the WebSocket layer uses, so
     * both API replicas hand out a byte-identical URL (a stable browser/SW cache key).
     */
    @Inject
    RedisDataSource redisDataSource;

    @ConfigProperty(name = "vlettenrally.pictures.bucket")
    String bucket;

    @ConfigProperty(name = "vlettenrally.pictures.view-url-reuse")
    Duration viewUrlReuse;

    @Override
    public String presignUpload(String objectKey, String contentType, Duration ttl) {
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(objectRequest)
                .build();

        return presigner.presignPutObject(presignRequest).url().toString();
    }

    @Override
    public String presignView(String objectKey, Duration ttl) {
        // The SDK presigner stamps each URL with Instant.now(), so two calls never produce
        // the same string and every gallery refresh would be a fresh cache key -> a fresh
        // Storj download. We cache the generated URL in Valkey so all replicas reuse one
        // identical, cacheable URL until the reuse window rolls over.
        String cacheKey = VIEW_URL_KEY_PREFIX + objectKey;
        ValueCommands<String, String> cache = redisDataSource.value(String.class);

        try {
            String cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        } catch (RuntimeException e) {
            // Fail open: a Valkey hiccup must not break picture viewing, just skip caching.
            Log.warnf("Failed to read cached view URL for %s: %s", objectKey, e.getMessage());
            return presignViewUrl(objectKey, ttl);
        }

        String url = presignViewUrl(objectKey, ttl);
        try {
            // SET .. NX GET: first replica to write wins; a racing replica gets that same
            // URL back instead of overwriting, so the two pods never diverge mid-window.
            String winner = cache.setGet(cacheKey, url, new SetArgs().nx().ex(viewUrlReuse));
            return winner != null ? winner : url;
        } catch (RuntimeException e) {
            Log.warnf("Failed to cache view URL for %s: %s", objectKey, e.getMessage());
            return url;
        }
    }

    private String presignViewUrl(String objectKey, Duration ttl) {
        // Tell the gateway to return a long-lived, public Cache-Control on the response so
        // the browser HTTP cache keeps the bytes too. Bounded by the reuse window: a served
        // URL is at most one window old, so it still has >= ttl - reuse validity remaining.
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .responseCacheControl("public, max-age=" + viewUrlReuse.toSeconds())
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(objectRequest)
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public Optional<ObjectMetadata> head(String objectKey) {
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return Optional.of(new ObjectMetadata(
                    response.contentLength() != null ? response.contentLength() : 0L,
                    response.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<InputStream> openObject(String objectKey) {
        try {
            // ResponseInputStream streams the body lazily, so the object is never held whole
            // in memory - the bulk download pipes it straight into the zip.
            return Optional.of(s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
        } catch (RuntimeException e) {
            Log.warnf("Failed to delete object %s from bucket %s: %s", objectKey, bucket, e.getMessage());
        }
    }
}
