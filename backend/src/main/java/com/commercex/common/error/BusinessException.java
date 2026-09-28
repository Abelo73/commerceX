package com.commercex.common.error;

import java.util.Map;

/**
 * Base for expected, client-facing failures. The message becomes the problem {@code detail} and
 * {@code details} is rendered as an extra problem property, so neither may contain sensitive data.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BusinessException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
