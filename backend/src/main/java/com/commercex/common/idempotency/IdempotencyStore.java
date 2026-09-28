package com.commercex.common.idempotency;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persistence for {@code idempotency_keys}. See {@link Idempotency} for the protocol. */
@Component
class IdempotencyStore {

    record Stored(long id, String requestHash, String status, Integer responseStatus, String responseBody,
            Instant createdAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    IdempotencyStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims the key in its own committed transaction. Returns the new row's ID, or empty if the key
     * already exists. Takes over an IN_PROGRESS claim older than {@code staleAfter}: its business
     * transaction never committed (completion is atomic with the business data), so retrying is safe.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> claim(long userId, String operation, String key, String requestHash, Instant expiresAt,
            Duration staleAfter) {
        MapSqlParameterSource params = new MapSqlParameterSource("userId", userId)
                .addValue("operation", operation)
                .addValue("key", key)
                .addValue("hash", requestHash)
                .addValue("expiresAt", Timestamp.from(expiresAt))
                .addValue("staleSeconds", staleAfter.toSeconds());
        Optional<Long> inserted = jdbc.queryForList("""
                INSERT INTO idempotency_keys (idem_key, user_id, operation, request_hash, status, expires_at)
                VALUES (:key, :userId, :operation, :hash, 'IN_PROGRESS', :expiresAt)
                ON CONFLICT (user_id, operation, idem_key) DO NOTHING
                RETURNING id""", params, Long.class).stream().findFirst();
        if (inserted.isPresent()) {
            return inserted;
        }
        return jdbc.queryForList("""
                UPDATE idempotency_keys SET created_at = now(), expires_at = :expiresAt
                WHERE user_id = :userId AND operation = :operation AND idem_key = :key
                  AND status = 'IN_PROGRESS' AND request_hash = :hash AND created_at < now() - make_interval(secs => :staleSeconds)
                RETURNING id""", params, Long.class).stream().findFirst();
    }

    public Optional<Stored> find(long userId, String operation, String key) {
        return jdbc.query("""
                SELECT id, request_hash, status, response_status, response_body::text AS body, created_at
                FROM idempotency_keys WHERE user_id = :userId AND operation = :operation AND idem_key = :key""",
                new MapSqlParameterSource("userId", userId).addValue("operation", operation).addValue("key", key),
                (rs, n) -> new Stored(rs.getLong("id"), rs.getString("request_hash"), rs.getString("status"),
                        rs.getObject("response_status", Integer.class), rs.getString("body"),
                        rs.getTimestamp("created_at").toInstant())).stream().findFirst();
    }

    /** Runs inside the business transaction, so the key completes if and only if the business data commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(long id, int responseStatus, String responseBody) {
        int updated = jdbc.update("""
                UPDATE idempotency_keys SET status = 'COMPLETED', response_status = :status,
                       response_body = CAST(:body AS JSONB)
                WHERE id = :id AND status = 'IN_PROGRESS'""",
                new MapSqlParameterSource("id", id).addValue("status", responseStatus).addValue("body", responseBody));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS,
                    "Idempotency key was taken over by another request");
        }
    }

    /** Frees the key after a failed attempt so the client can retry with it. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(long id) {
        jdbc.update("DELETE FROM idempotency_keys WHERE id = :id AND status = 'IN_PROGRESS'",
                new MapSqlParameterSource("id", id));
    }

    @Transactional
    public int deleteExpired() {
        return jdbc.update("DELETE FROM idempotency_keys WHERE expires_at < now()", new MapSqlParameterSource());
    }
}
