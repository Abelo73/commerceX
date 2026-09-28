package com.commercex.common.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Envelope of every message on Kafka. {@code eventId} is the deduplication key for consumers;
 * {@code data} is the event-specific payload. Integration events are a public contract: add fields,
 * never rename or remove them.
 */
public record IntegrationEvent(UUID eventId, String type, Instant occurredAt, JsonNode data) {
}
