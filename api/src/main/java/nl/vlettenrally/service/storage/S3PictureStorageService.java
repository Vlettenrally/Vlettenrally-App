package nl.vlettenrally.service.storage;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.logging.Log;
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

import java.time.Duration;
import java.util.Optional;

/**
 * Production picture storage backed by the Storj S3 gateway.
 * Only active in prod profile - replaced by MockPictureStorageService in dev/test.
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class S3PictureStorageService implements PictureStorageService {

    @Inject
    S3Presigner presigner;

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "vlettenrally.pictures.bucket")
    String bucket;

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
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
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
