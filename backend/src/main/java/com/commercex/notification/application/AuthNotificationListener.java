package com.commercex.notification.application;

import com.commercex.auth.PasswordResetRequested;
import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to auth events after the publishing transaction commits, asynchronously, so a slow or
 * failing mail server never blocks or rolls back the request. In-process delivery is best effort
 * (lost on crash); reliable delivery arrives with the outbox in Phase 9.
 */
@Component
class AuthNotificationListener {

    private final EmailSender emailSender;

    AuthNotificationListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(PasswordResetRequested event) {
        long minutes = Math.max(1, Duration.between(Instant.now(), event.expiresAt()).toMinutes());
        String body = """
                Hi %s,

                We received a request to reset your CommerceX password.
                Use the link below within %d minutes to choose a new one:

                %s

                If you did not request this, you can ignore this email; your password will not change.
                """.formatted(event.firstName(), minutes, event.resetUrl());
        emailSender.send(new EmailMessage(event.email(), "Reset your CommerceX password", body));
    }
}
