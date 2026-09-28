package com.commercex.common.idempotency;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.ValidationException;
import com.commercex.common.security.SecureTokens;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exactly-once execution of critical operations keyed by the client's {@code Idempotency-Key} (TRD §10).
 *
 * <pre>
 * claim key (own tx) ──► already COMPLETED?  → replay stored response
 *                    ──► IN_PROGRESS?        → 409 (another request is running)
 *                    ──► different request?  → 422
 *                    ──► claimed             → run operation + mark COMPLETED in ONE transaction
 *                                               (on failure: release the key so the client may retry)
 * </pre>
 */
@Component
public class Idempotency {

    public static final String HEADER = "Idempotency-Key";

    private static final Logger log = LoggerFactory.getLogger(Idempotency.class);
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_.:-]{8,255}");
    private static final Duration RETENTION = Duration.ofHours(24);
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    /** {@code body} is the operation's result, or the stored JSON when {@code replayed}. */
    public record Result<T>(int status, T body, JsonNode replayedBody, boolean replayed) {
    }

    private final IdempotencyStore store;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    Idempotency(IdempotencyStore store, TransactionTemplate transactions, ObjectMapper objectMapper, Clock clock) {
        this.store = store;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @param request   the parsed request body; its JSON form is hashed to detect key reuse with other input
     * @param operation runs in a new transaction that also records the response
     */
    public <T> Result<T> execute(long userId, String operationName, String key, Object request, int successStatus,
            Supplier<T> operation) {
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new ValidationException(HEADER, "header is required: 8-255 characters of [A-Za-z0-9_.:-]");
        }
        String requestHash = SecureTokens.sha256(json(request));

        Optional<Long> claimed = store.claim(userId, operationName, key, requestHash,
                clock.instant().plus(RETENTION), STALE_AFTER);
        if (claimed.isEmpty()) {
            return replayOrReject(userId, operationName, key, requestHash);
        }
        long keyId = claimed.get();
        try {
            T body = transactions.execute(status -> {
                T result = operation.get();
                store.complete(keyId, successStatus, json(result));
                return result;
            });
            return new Result<>(successStatus, body, null, false);
        } catch (RuntimeException e) {
            store.release(keyId);
            throw e;
        }
    }

    private <T> Result<T> replayOrReject(long userId, String operationName, String key, String requestHash) {
        IdempotencyStore.Stored stored = store.find(userId, operationName, key)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS,
                        "Request with this idempotency key is being retried; try again"));
        if (!stored.requestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_MISMATCH,
                    "This idempotency key was already used with a different request");
        }
        if (!"COMPLETED".equals(stored.status())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS,
                    "A request with this idempotency key is still being processed");
        }
        try {
            return new Result<>(stored.responseStatus(), null, objectMapper.readTree(stored.responseBody()), true);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is not valid JSON", e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize for idempotency", e);
        }
    }

    @Scheduled(cron = "${commercex.idempotency.cleanup-cron:0 23 * * * *}")
    void deleteExpiredKeys() {
        int deleted = store.deleteExpired();
        if (deleted > 0) {
            log.info("Deleted {} expired idempotency keys", deleted);
        }
    }
}
