package com.commercex.auth.application;

import java.time.Instant;

/**
 * Outcome of register/login/refresh. {@code refreshToken} is the raw secret: the API layer puts it
 * in an httpOnly cookie and never in a response body or log.
 */
public record AuthResult(String accessToken, Instant accessTokenExpiresAt, long expiresInSeconds,
        String refreshToken, Instant refreshTokenExpiresAt, UserView user) {

    @Override
    public String toString() {
        return "AuthResult[user=" + user.id() + ", accessTokenExpiresAt=" + accessTokenExpiresAt + "]";
    }
}
