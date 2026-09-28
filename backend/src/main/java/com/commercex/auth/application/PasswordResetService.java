package com.commercex.auth.application;

import com.commercex.auth.PasswordResetRequested;
import com.commercex.auth.domain.PasswordResetToken;
import com.commercex.auth.domain.User;
import com.commercex.auth.infrastructure.PasswordResetTokenRepository;
import com.commercex.auth.infrastructure.RefreshTokenRepository;
import com.commercex.auth.infrastructure.UserRepository;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.ratelimit.RateLimiter;
import com.commercex.common.security.SecureTokens;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PasswordResetService(UserRepository users, PasswordResetTokenRepository resetTokens,
            RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            RateLimiter rateLimiter, AuthProperties properties, AuditLog auditLog, ApplicationEventPublisher events,
            Clock clock) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.auditLog = auditLog;
        this.events = events;
        this.clock = clock;
    }

    /** Always succeeds from the caller's point of view, so it cannot be used to discover accounts. */
    @Transactional
    public void request(String email, String clientIp) {
        String normalizedEmail = Emails.normalize(email);
        rateLimiter.consume("reset:ip:" + clientIp, properties.rateLimits().passwordResetPerIp());
        rateLimiter.consume("reset:email:" + normalizedEmail, properties.rateLimits().passwordResetPerEmail());

        users.findByEmail(normalizedEmail).filter(User::isActive).ifPresent(user -> {
            Instant now = clock.instant();
            resetTokens.invalidateOutstanding(user.getId(), now);
            String rawToken = SecureTokens.generate();
            Instant expiresAt = now.plus(properties.passwordResetTtl());
            resetTokens.save(PasswordResetToken.issue(user.getId(), SecureTokens.sha256(rawToken), now, expiresAt));
            String resetUrl = UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                    .path("/reset-password")
                    .queryParam("token", rawToken)
                    .toUriString();
            auditLog.record(user.getId(), "PASSWORD_RESET_REQUESTED", "user", user.getId(), null);
            events.publishEvent(new PasswordResetRequested(user.getId(), user.getEmail(), user.getFirstName(),
                    resetUrl, expiresAt));
        });
    }

    /** Sets the new password and signs the user out everywhere. */
    @Transactional
    public void confirm(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHashForUpdate(SecureTokens.sha256(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN,
                        "This password reset link is invalid or has expired"));
        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN,
                        "This password reset link is invalid or has expired"));
        passwordPolicy.check(newPassword, user.getEmail(), "newPassword");

        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        token.markUsed(now);
        refreshTokens.revokeAllForUser(user.getId(), now);
        auditLog.record(user.getId(), "PASSWORD_RESET_COMPLETED", "user", user.getId(), null);
    }
}
