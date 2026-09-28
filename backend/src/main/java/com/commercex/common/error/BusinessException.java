package com.commercex.common.error;

/**
 * Base for expected, client-facing failures. The message becomes the problem {@code detail},
 * so it must never contain sensitive data.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
