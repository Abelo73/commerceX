package com.commercex.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends an integration event to the transactional outbox. Must run inside the business transaction:
 * the event exists if and only if the business change commits. {@link OutboxRelay} publishes it later.
 * Never put secrets (tokens, passwords, card data) in an event.
 */
@Component
public class Outbox {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    Outbox(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(String topic, Object key, String type, Object data) {
        UUID eventId = UUID.randomUUID();
        IntegrationEvent event = new IntegrationEvent(eventId, type, clock.instant(), objectMapper.valueToTree(data));
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + type, e);
        }
        jdbc.update("""
                INSERT INTO outbox_events (event_id, topic, message_key, event_type, payload)
                VALUES (:eventId, :topic, :key, :type, CAST(:payload AS JSONB))""",
                new MapSqlParameterSource("eventId", eventId).addValue("topic", topic)
                        .addValue("key", String.valueOf(key)).addValue("type", type).addValue("payload", payload));
        return eventId;
    }
}
