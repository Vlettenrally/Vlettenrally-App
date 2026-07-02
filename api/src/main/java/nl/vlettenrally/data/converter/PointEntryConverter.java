package nl.vlettenrally.data.converter;

import jakarta.enterprise.context.ApplicationScoped;
import nl.vlettenrally.data.domain.PointEntry;
import nl.vlettenrally.data.dto.points.PointEntryResponse;
import org.locationtech.jts.geom.Point;

@ApplicationScoped
public class PointEntryConverter {

    public PointEntryResponse toResponse(PointEntry entry) {
        if (entry == null) return null;
        Point loc = entry.getLocation();
        return new PointEntryResponse(
            entry.getId(),
            entry.getTeamId(),
            entry.getGameId(),
            entry.getEntryType() != null ? entry.getEntryType().name() : null,
            entry.getOriginalAmount(),
            entry.getCurrentAmount(),
            entry.getDistanceMeters(),
            entry.getSourceAchievementId(),
            loc != null ? loc.getY() : null,
            loc != null ? loc.getX() : null,
            entry.getDescription(),
            entry.getCreatedAt(),
            entry.getUpdatedAt(),
            entry.getCreatedBy(),
            entry.getEditedAt(),
            entry.getEditedBy(),
            entry.getEditReason()
        );
    }
}
