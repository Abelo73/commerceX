package com.commercex;

import com.commercex.notification.application.EmailMessage;
import com.commercex.notification.application.EmailSender;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

/** Replaces SMTP in integration tests; messages are delivered asynchronously, so tests await them. */
@TestComponent
@Primary
public class CapturingEmailSender implements EmailSender {

    private final ConcurrentLinkedQueue<EmailMessage> sent = new ConcurrentLinkedQueue<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    /** Latest message to the recipient whose subject contains {@code subjectPart}. */
    public Optional<EmailMessage> awaitMessage(String recipient, String subjectPart, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<EmailMessage> found = sentTo(recipient, subjectPart).stream().reduce((first, second) -> second);
            if (found.isPresent()) {
                return found;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public List<EmailMessage> sentTo(String recipient, String subjectPart) {
        return sent.stream()
                .filter(m -> m.to().equalsIgnoreCase(recipient) && m.subject().contains(subjectPart))
                .toList();
    }
}
