package com.commercex.auth.api;

import com.commercex.auth.application.AuthProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the httpOnly refresh-token cookie. The token is never exposed to JavaScript. */
@Component
class RefreshTokenCookies {

    private final AuthProperties.RefreshCookie config;
    private final Clock clock;

    RefreshTokenCookies(AuthProperties properties, Clock clock) {
        this.config = properties.refreshCookie();
        this.clock = clock;
    }

    String name() {
        return config.name();
    }

    ResponseCookie issue(String rawToken, Instant expiresAt) {
        return base(rawToken).maxAge(Duration.between(clock.instant(), expiresAt)).build();
    }

    ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(config.name(), value)
                .httpOnly(true)
                .secure(config.secure())
                .sameSite(config.sameSite())
                .path(config.path());
    }
}
