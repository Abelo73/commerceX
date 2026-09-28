package com.commercex.common.ratelimit;

/**
 * Throws {@link RateLimitExceededException} when {@code key} has exhausted its limit.
 * Keys should be namespaced, e.g. {@code "login:ip:203.0.113.7"}.
 */
public interface RateLimiter {

    void consume(String key, RateLimit limit);
}
