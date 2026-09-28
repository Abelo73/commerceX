package com.commercex.common.ratelimit;

import java.time.Duration;

/** Allow {@code capacity} requests per {@code period} (token bucket, greedy refill). */
public record RateLimit(int capacity, Duration period) {

    public RateLimit {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (period == null || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("period must be positive");
        }
    }
}
