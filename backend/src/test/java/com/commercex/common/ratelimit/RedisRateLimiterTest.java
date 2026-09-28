package com.commercex.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** TRD §20: "What happens if Redis is unavailable?" — limits keep working per instance. */
class RedisRateLimiterTest {

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToInMemoryLimiterWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        RedisRateLimiter limiter = new RedisRateLimiter(redis, new InMemoryRateLimiter(), new SimpleMeterRegistry());
        RateLimit twoPerHour = new RateLimit(2, Duration.ofHours(1));

        limiter.consume("login:ip:1.2.3.4", twoPerHour);
        limiter.consume("login:ip:1.2.3.4", twoPerHour);
        assertThatThrownBy(() -> limiter.consume("login:ip:1.2.3.4", twoPerHour))
                .isInstanceOf(RateLimitExceededException.class);
    }
}
