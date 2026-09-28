package com.commercex.notification.application;

/** Channel abstraction: SMTP today; SMS/push senders plug in beside it without touching business logic. */
public interface EmailSender {

    void send(EmailMessage message);
}
