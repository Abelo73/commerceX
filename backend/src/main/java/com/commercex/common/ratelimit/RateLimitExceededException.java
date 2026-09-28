package com.commercex.common.ratelimit;

import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;

public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, "Too many requests; retry later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
