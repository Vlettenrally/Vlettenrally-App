package nl.vlettenrally.data.dto.achievement;

import java.util.UUID;

public record ProximityDetectionRequest(
    UUID teamId,
    UUID checkpointId
) {}
