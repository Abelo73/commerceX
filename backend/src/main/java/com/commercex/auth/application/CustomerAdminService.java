package com.commercex.auth.application;

import com.commercex.auth.domain.Role;
import com.commercex.auth.domain.User;
import com.commercex.auth.domain.UserStatus;
import com.commercex.auth.infrastructure.RefreshTokenRepository;
import com.commercex.auth.infrastructure.UserRepository;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrative user management. Callers must be ADMIN (enforced in the API layer). */
@Service
public class CustomerAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLog auditLog;
    private final Clock clock;

    CustomerAdminService(UserRepository users, RefreshTokenRepository refreshTokens, AuditLog auditLog,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(String emailQuery, Pageable pageable) {
        Page<User> page = emailQuery == null || emailQuery.isBlank()
                ? users.findAll(pageable)
                : users.findByEmailContainingIgnoreCase(emailQuery.trim(), pageable);
        return page.map(UserView::from);
    }

    /**
     * Changes status and/or roles. Admins cannot lock themselves out or drop their own ADMIN role,
     * which prevents accidentally leaving the system without an administrator.
     */
    @Transactional
    public UserView update(long actorUserId, long userId, UserStatus status, Set<Role> roles) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        boolean self = actorUserId == userId;
        if (self && status != null && status != UserStatus.ACTIVE) {
            throw new ConflictException("You cannot deactivate your own account");
        }
        if (self && roles != null && !roles.contains(Role.ADMIN)) {
            throw new ConflictException("You cannot remove your own ADMIN role");
        }

        Instant now = clock.instant();
        Map<String, Object> changes = new LinkedHashMap<>();
        if (status != null && status != user.getStatus()) {
            changes.put("status", user.getStatus() + "->" + status);
            user.changeStatus(status, now);
            if (status != UserStatus.ACTIVE) {
                refreshTokens.revokeAllForUser(userId, now);
            }
        }
        if (roles != null && !roles.equals(user.getRoles())) {
            changes.put("roles", user.getRoles() + "->" + roles);
            user.replaceRoles(roles, now);
            // Existing access tokens keep old roles until they expire (max access-token TTL).
            refreshTokens.revokeAllForUser(userId, now);
        }
        if (!changes.isEmpty()) {
            auditLog.record(actorUserId, "USER_UPDATED", "user", userId, changes);
        }
        return UserView.from(user);
    }
}
