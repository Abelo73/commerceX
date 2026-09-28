package com.commercex.auth.application;

import com.commercex.auth.UserRegistered;
import com.commercex.common.messaging.Outbox;
import com.commercex.common.messaging.Topics;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Publishes user lifecycle events via the outbox. Password-reset events are deliberately NOT published. */
@Component
class UserIntegrationEvents {

    record UserRegisteredV1(long userId, String email, String firstName) {
    }

    private final Outbox outbox;

    UserIntegrationEvents(Outbox outbox) {
        this.outbox = outbox;
    }

    @EventListener
    void on(UserRegistered event) {
        outbox.append(Topics.USERS, event.userId(), "UserRegistered",
                new UserRegisteredV1(event.userId(), event.email(), event.firstName()));
    }
}
