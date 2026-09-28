package com.commercex.auth.application;

import com.commercex.auth.domain.Role;
import com.commercex.auth.domain.User;
import com.commercex.auth.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first administrator from configuration when the system has none. */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuthProperties properties;
    private final Clock clock;

    AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            AuthProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || isBlank(admin.email()) || isBlank(admin.password()) || users.existsWithRole(Role.ADMIN)) {
            return;
        }
        String email = Emails.normalize(admin.email());
        passwordPolicy.check(admin.password(), email, "commercex.auth.bootstrap-admin.password");
        Instant now = clock.instant();
        User user = users.findByEmail(email).orElseGet(() -> users.save(User.register(email,
                passwordEncoder.encode(admin.password()), orDefault(admin.firstName(), "Admin"),
                orDefault(admin.lastName(), "User"), now)));
        EnumSet<Role> roles = EnumSet.of(Role.ADMIN);
        roles.addAll(user.getRoles());
        user.replaceRoles(roles, now);
        log.info("Bootstrapped administrator account {}", email);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}
