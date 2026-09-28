package com.commercex.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.commercex.common.error.ValidationException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsReasonablePasswords() {
        assertThatCode(() -> policy.check("Correct-Horse-9", "a@b.com", "password")).doesNotThrowAnyException();
        assertThatCode(() -> policy.check("x".repeat(72), "a@b.com", "password")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShortCountingCodePoints() {
        assertThatThrownBy(() -> policy.check("short", null, "password")).isInstanceOf(ValidationException.class);
        // 5 emoji = 10 UTF-16 chars but only 5 characters
        assertThatThrownBy(() -> policy.check("😀".repeat(5), null, "password"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsMoreThan72Bytes() {
        assertThatThrownBy(() -> policy.check("x".repeat(73), null, "password"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> policy.check("é".repeat(37), null, "password")) // 74 bytes
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsEmailAsPassword() {
        assertThatThrownBy(() -> policy.check("Someone@Example.com", "someone@example.com", "password"))
                .isInstanceOf(ValidationException.class);
    }
}
