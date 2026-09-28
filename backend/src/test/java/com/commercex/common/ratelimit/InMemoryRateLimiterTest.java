package com.commercex.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter();
    private final RateLimit threePerHour = new RateLimit(3, Duration.ofHours(1));

    @Test
    void allowsUpToCapacityThenRejectsWithRetryAfter() {
        for (int i = 0; i < 3; i++) {
            limiter.consume("k", threePerHour);
        }
        assertThatThrownBy(() -> limiter.consume("k", threePerHour))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        ex -> assertThat(ex.retryAfterSeconds()).isBetween(1L, 3600L));
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.consume("a", threePerHour);
        }
        limiter.consume("b", threePerHour);
    }

    @Test
    void rejectsInvalidLimits() {
        assertThatThrownBy(() -> new RateLimit(0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimit(1, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
