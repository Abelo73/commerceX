package com.commercex.auth;

import java.time.Instant;

/**
 * Published after a password reset was requested for an existing, active account.
 * {@code resetUrl} contains a secret token: it must only be delivered to the user's inbox,
 * never logged or externalized to a broker.
 */
public record PasswordResetRequested(long userId, String email, String firstName, String resetUrl,
        Instant expiresAt) {

    @Override
    public String toString() {
        return "PasswordResetRequested[userId=" + userId + ", expiresAt=" + expiresAt + "]";
    }
}
