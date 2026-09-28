package com.commercex.auth.application;

import com.commercex.auth.domain.User;
import com.commercex.auth.infrastructure.RefreshTokenRepository;
import com.commercex.auth.infrastructure.UserRepository;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service profile operations for the authenticated user. */
@Service
public class AccountService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditLog auditLog;
    private final Clock clock;

    AccountService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, AuditLog auditLog, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserView profile(long userId) {
        return UserView.from(load(userId));
    }

    @Transactional
    public UserView updateProfile(long userId, String firstName, String lastName) {
        User user = load(userId);
        user.rename(firstName.trim(), lastName.trim(), clock.instant());
        return UserView.from(user);
    }

    /** Revokes every refresh token, so all other devices must sign in again. */
    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        User user = load(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ValidationException("currentPassword", "is incorrect");
        }
        passwordPolicy.check(newPassword, user.getEmail(), "newPassword");
        Instant now = clock.instant();
        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        refreshTokens.revokeAllForUser(userId, now);
        auditLog.record(userId, "PASSWORD_CHANGED", "user", userId, null);
    }

    private User load(long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
