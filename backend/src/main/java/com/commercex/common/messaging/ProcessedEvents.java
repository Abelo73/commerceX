package com.commercex.common.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side idempotency. Call inside the consumer's transaction before doing the work: if the work
 * fails, the marker rolls back with it and the redelivered event is processed again.
 */
@Component
public class ProcessedEvents {

    private final NamedParameterJdbcTemplate jdbc;

    ProcessedEvents(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns false if this consumer already processed the event (duplicate delivery). */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markIfNew(String consumer, UUID eventId) {
        return jdbc.update("""
                INSERT INTO processed_events (consumer, event_id) VALUES (:consumer, :eventId)
                ON CONFLICT (consumer, event_id) DO NOTHING""",
                new MapSqlParameterSource("consumer", consumer).addValue("eventId", eventId)) == 1;
    }
}
