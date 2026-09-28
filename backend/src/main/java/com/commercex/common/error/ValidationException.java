package com.commercex.common.error;

import java.util.List;

/**
 * Validation failure detected in application code (beyond Bean Validation), reported with field errors.
 */
public class ValidationException extends BusinessException {

    private final List<FieldViolation> violations;

    public ValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_FAILED, "Request has invalid fields");
        this.violations = List.of(new FieldViolation(field, message));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
