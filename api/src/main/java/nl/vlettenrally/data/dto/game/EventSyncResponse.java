package nl.vlettenrally.data.dto.game;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Result of a sync-event-details run: how many checkpoints were created/updated
 * and how many teams got their externalParticipantId linked from the tracker.
 */
@RegisterForReflection
public record EventSyncResponse(
        int checkpointsCreated,
        int checkpointsUpdated,
        int checkpointErrors,
        int participantsLinked,
        int participantsUnmatched
) {}
