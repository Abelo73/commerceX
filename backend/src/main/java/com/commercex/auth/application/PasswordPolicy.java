package com.commercex.auth.application;

import com.commercex.common.error.ValidationException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Length-based policy (NIST SP 800-63B). BCrypt silently ignores bytes beyond 72,
 * so longer passwords are rejected rather than truncated.
 */
@Component
class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_BYTES = 72;

    void check(String password, String email, String field) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new ValidationException(field, "must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ValidationException(field, "must be at most " + MAX_BYTES + " bytes");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw new ValidationException(field, "must not be the same as the email address");
        }
    }
}
