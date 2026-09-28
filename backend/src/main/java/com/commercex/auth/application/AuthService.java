package com.commercex.auth.application;

import com.commercex.auth.UserRegistered;
import com.commercex.auth.domain.RefreshToken;
import com.commercex.auth.domain.User;
import com.commercex.auth.infrastructure.RefreshTokenRepository;
import com.commercex.auth.infrastructure.UserRepository;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.BusinessException;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.ratelimit.RateLimiter;
import com.commercex.common.security.SecureTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenIssuer tokenIssuer;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Compared against when the email is unknown so both failure paths cost one bcrypt check. */
    private final String dummyHash;

    AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, TokenIssuer tokenIssuer, RateLimiter rateLimiter,
            AuthProperties properties, AuditLog auditLog, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenIssuer = tokenIssuer;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.auditLog = auditLog;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(SecureTokens.generate());
    }

    @Transactional
    public AuthResult register(String email, String password, String firstName, String lastName, String clientIp) {
        rateLimiter.consume("register:ip:" + clientIp, properties.rateLimits().registerPerIp());
        String normalizedEmail = Emails.normalize(email);
        passwordPolicy.check(password, normalizedEmail, "password");
        if (users.existsByEmail(normalizedEmail)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
        }
        User user;
        try {
            user = users.saveAndFlush(User.register(normalizedEmail, passwordEncoder.encode(password),
                    firstName.trim(), lastName.trim(), clock.instant()));
        } catch (DataIntegrityViolationException raceOnUniqueEmail) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
        }
        auditLog.record(user.getId(), "USER_REGISTERED", "user", user.getId(), null);
        events.publishEvent(new UserRegistered(user.getId(), user.getEmail(), user.getFirstName()));
        return tokenIssuer.issueNewSession(user);
    }

    @Transactional
    public AuthResult login(String email, String password, String clientIp) {
        String normalizedEmail = Emails.normalize(email);
        rateLimiter.consume("login:ip:" + clientIp, properties.rateLimits().loginPerIp());
        rateLimiter.consume("login:email:" + normalizedEmail, properties.rateLimits().loginPerEmail());

        Optional<User> found = users.findByEmail(normalizedEmail);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            auditLog.record(null, "LOGIN_FAILED", "user", null, Map.of("reason", "unknown_email"));
            throw invalidCredentials();
        }
        User user = found.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditLog.record(user.getId(), "LOGIN_FAILED", "user", user.getId(), Map.of("reason", "bad_password"));
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            auditLog.record(user.getId(), "LOGIN_BLOCKED", "user", user.getId(),
                    Map.of("status", user.getStatus().name()));
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "This account is not active");
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePasswordHash(passwordEncoder.encode(password), clock.instant());
        }
        auditLog.record(user.getId(), "LOGIN_SUCCEEDED", "user", user.getId(), null);
        return tokenIssuer.issueNewSession(user);
    }

    /**
     * Rotates the refresh token. Presenting a token that was already rotated means it was copied:
     * the whole family is revoked, logging out both the attacker and the victim.
     * Business exceptions do not roll back, so revocations are committed before the error is returned.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);

        if (!current.isUsable(now)) {
            if (current.wasRotated()) {
                refreshTokens.revokeFamily(current.getFamilyId(), now);
                auditLog.record(current.getUserId(), "REFRESH_TOKEN_REUSE_DETECTED", "user", current.getUserId(),
                        Map.of("family", current.getFamilyId().toString()));
            }
            throw invalidRefreshToken();
        }

        User user = users.findById(current.getUserId()).orElseThrow(AuthService::invalidRefreshToken);
        if (!user.isActive()) {
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "This account is not active");
        }

        TokenIssuer.Issued next = tokenIssuer.issue(user, current.getFamilyId());
        current.rotatedTo(next.refreshToken(), now);
        return next.result();
    }

    /** Idempotent: unknown or already-revoked tokens are ignored. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHashForUpdate(SecureTokens.sha256(rawRefreshToken)).ifPresent(token -> {
            refreshTokens.revokeFamily(token.getFamilyId(), clock.instant());
            auditLog.record(token.getUserId(), "LOGOUT", "user", token.getUserId(), null);
        });
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    private static BusinessException invalidRefreshToken() {
        return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid or expired");
    }
}
