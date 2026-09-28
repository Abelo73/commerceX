package com.commercex.payment.infrastructure;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEventRepository {

    private final NamedParameterJdbcTemplate jdbc;

    WebhookEventRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns false if this provider event was already recorded (duplicate delivery). */
    public boolean insertIfNew(String provider, String eventId, String type, String payload) {
        return !jdbc.queryForList("""
                INSERT INTO payment_webhook_events (provider, provider_event_id, event_type, payload, outcome)
                VALUES (:provider, :eventId, :type, CAST(:payload AS JSONB), 'RECEIVED')
                ON CONFLICT (provider, provider_event_id) DO NOTHING
                RETURNING id""",
                new MapSqlParameterSource("provider", provider).addValue("eventId", eventId).addValue("type", type)
                        .addValue("payload", payload), Long.class).isEmpty();
    }

    public void setOutcome(String provider, String eventId, String outcome) {
        jdbc.update("""
                UPDATE payment_webhook_events SET outcome = :outcome
                WHERE provider = :provider AND provider_event_id = :eventId""",
                new MapSqlParameterSource("provider", provider).addValue("eventId", eventId)
                        .addValue("outcome", outcome));
    }
}
