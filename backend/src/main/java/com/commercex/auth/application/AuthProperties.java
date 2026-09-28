package com.commercex.auth.application;

import com.commercex.common.ratelimit.RateLimit;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("commercex.auth")
public record AuthProperties(
        Duration refreshTokenTtl,
        Duration passwordResetTtl,
        String frontendBaseUrl,
        RefreshCookie refreshCookie,
        RateLimits rateLimits,
        BootstrapAdmin bootstrapAdmin) {

    public record RefreshCookie(String name, String path, boolean secure, String sameSite) {
    }

    public record RateLimits(
            RateLimit loginPerIp,
            RateLimit loginPerEmail,
            RateLimit registerPerIp,
            RateLimit passwordResetPerIp,
            RateLimit passwordResetPerEmail) {
    }

    /** Creates the first ADMIN on startup when no admin exists. Leave blank to disable. */
    public record BootstrapAdmin(String email, String password, String firstName, String lastName) {

        @Override
        public String toString() {
            return "BootstrapAdmin[email=" + email + "]";
        }
    }
}
