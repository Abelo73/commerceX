package com.commercex.common.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Cluster-wide fixed-window limiter in Redis (atomic Lua: INCR + PEXPIRE). If Redis is unavailable it
 * falls back to the per-instance in-memory limiter rather than failing every request (TRD §20): limits
 * stay enforced per node while Redis recovers.
 */
@Component
@Primary
@ConditionalOnProperty(name = "commercex.rate-limit.store", havingValue = "redis")
class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> WINDOW = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return {current, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final InMemoryRateLimiter fallback;
    private final MeterRegistry meters;

    RedisRateLimiter(StringRedisTemplate redis, InMemoryRateLimiter fallback, MeterRegistry meters) {
        this.redis = redis;
        this.fallback = fallback;
        this.meters = meters;
    }

    @Override
    public void consume(String key, RateLimit limit) {
        String redisKey = "rl:" + key + ":" + limit.capacity() + "/" + limit.period().toSeconds();
        List<?> result;
        try {
            result = redis.execute(WINDOW, List.of(redisKey), String.valueOf(limit.period().toMillis()));
        } catch (DataAccessException | IllegalStateException e) {
            meters.counter("commercex.ratelimit.fallback").increment();
            log.warn("Redis rate limiter unavailable, using in-memory fallback: {}", e.getMessage());
            fallback.consume(key, limit);
            return;
        }
        long count = ((Number) result.get(0)).longValue();
        long ttlMillis = ((Number) result.get(1)).longValue();
        if (count > limit.capacity()) {
            throw new RateLimitExceededException(Math.max(1, (ttlMillis + 999) / 1000));
        }
    }
}
