package nl.vlettenrally.data.dto.message;

import java.util.UUID;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Payload for MESSAGE_DELIVERY_UPDATE websocket events.
 * Notifies clients when a message's delivery or read status changes.
 */
@RegisterForReflection
public record MessageDeliveryUpdatePayload(
    UUID messageId,
    UUID batchId,
    UUID teamId,
    boolean delivered,
    boolean read
) {}
