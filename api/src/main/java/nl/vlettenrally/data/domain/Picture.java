package nl.vlettenrally.data.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import nl.vlettenrally.data.enums.PictureStatus;

/**
 * Domain object representing a team-uploaded picture.
 * <p>
 * The backend reserves a PENDING picture (with a unique object key) before issuing a
 * pre-signed upload URL. Once the browser uploads directly to S3 and confirms,
 * {@code uploadedAt} is set and the picture becomes eligible for admin moderation.
 */
@Setter
@Getter
public class Picture {
    private UUID id;
    private UUID teamId;
    private UUID gameId;
    private String objectKey;
    private PictureStatus status;
    private String contentType;
    private Long sizeBytes;
    private Instant createdAt;
    private Instant uploadedAt;     // null until the upload is confirmed
    private Instant moderatedAt;
    private UUID moderatedBy;
    private String notes;

    public Picture() {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.status = PictureStatus.PENDING;
    }

    public Picture(UUID id, UUID teamId, UUID gameId, String objectKey, PictureStatus status,
                   String contentType, Instant createdAt) {
        this.id = id;
        this.teamId = teamId;
        this.gameId = gameId;
        this.objectKey = objectKey;
        this.status = status;
        this.contentType = contentType;
        this.createdAt = createdAt;
    }

    /** True once the file has been uploaded and confirmed (eligible for moderation). */
    public boolean isUploaded() {
        return uploadedAt != null;
    }
}
