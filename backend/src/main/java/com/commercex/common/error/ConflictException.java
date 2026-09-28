package com.commercex.common.error;

import java.util.Map;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }

    public ConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
