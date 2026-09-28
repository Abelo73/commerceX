package com.commercex.common.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes pending outbox rows to Kafka.
 * <ul>
 *   <li>Rows are claimed with {@code FOR UPDATE SKIP LOCKED}: several instances can relay in parallel without
 *       publishing the same row twice concurrently.</li>
 *   <li>Delivery is at-least-once (a crash between send and commit re-sends); consumers deduplicate by eventId.</li>
 *   <li>On failure the row backs off exponentially (max 5 min) and the batch stops, so an outage does not
 *       stall the relay on timeouts.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "commercex.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private record Pending(long id, String eventId, String topic, String key, String type, String payload,
            int attempts) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transactions;
    private final Duration retention;

    OutboxRelay(NamedParameterJdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate transactions,
            MeterRegistry meters, @Value("${commercex.outbox.retention:7d}") Duration retention) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.transactions = transactions;
        this.retention = retention;
        Gauge.builder("commercex.outbox.pending", this::pendingCount)
                .description("Outbox events not yet published to Kafka")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${commercex.outbox.poll-interval-ms:1000}")
    public void relay() {
        int published;
        do {
            Integer result = transactions.execute(status -> publishBatch());
            published = result == null ? 0 : result;
        } while (published == BATCH);
    }

    /** Returns the number of rows published; stops at the first failure. */
    private int publishBatch() {
        List<Pending> batch = jdbc.query("""
                SELECT id, event_id, topic, message_key, event_type, payload::text AS payload, attempts
                FROM outbox_events
                WHERE published_at IS NULL AND next_attempt_at <= now()
                ORDER BY id
                LIMIT :batch
                FOR UPDATE SKIP LOCKED""", new MapSqlParameterSource("batch", BATCH),
                (rs, n) -> new Pending(rs.getLong("id"), rs.getString("event_id"), rs.getString("topic"),
                        rs.getString("message_key"), rs.getString("event_type"), rs.getString("payload"),
                        rs.getInt("attempts")));
        int published = 0;
        for (Pending row : batch) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
                record.headers().add("eventId", row.eventId().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", row.type().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(10, TimeUnit.SECONDS);
                jdbc.update("UPDATE outbox_events SET published_at = now(), attempts = attempts + 1 WHERE id = :id",
                        new MapSqlParameterSource("id", row.id()));
                published++;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                long backoffSeconds = Math.min(MAX_BACKOFF.toSeconds(), (long) Math.pow(2, Math.min(row.attempts(), 16)));
                log.warn("Outbox publish of {} ({}) failed, retry in {}s: {}", row.eventId(), row.type(),
                        backoffSeconds, e.toString());
                jdbc.update("""
                        UPDATE outbox_events
                        SET attempts = attempts + 1, last_error = left(:error, 1000),
                            next_attempt_at = now() + make_interval(secs => :backoff)
                        WHERE id = :id""",
                        new MapSqlParameterSource("id", row.id()).addValue("error", e.toString())
                                .addValue("backoff", backoffSeconds));
                return published; // stop this batch; the broker is probably unavailable
            }
        }
        return published;
    }

    @Scheduled(cron = "${commercex.outbox.cleanup-cron:0 41 * * * *}")
    void deletePublished() {
        int deleted = jdbc.update("DELETE FROM outbox_events WHERE published_at < now() - make_interval(secs => :s)",
                new MapSqlParameterSource("s", retention.toSeconds()));
        if (deleted > 0) {
            log.info("Deleted {} published outbox events", deleted);
        }
    }

    private double pendingCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL",
                new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }
}
