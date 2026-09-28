package com.commercex.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static final String SECRET = "test-webhook-secret-0123456789-abcdefghijkl";
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    @Test
    void acceptsValidSignature() {
        String header = WebhookSignature.sign(SECRET, NOW.getEpochSecond(), "{\"a\":1}");
        assertThat(WebhookSignature.verify(SECRET, header, "{\"a\":1}", NOW, TOLERANCE)).isTrue();
    }

    @Test
    void rejectsTamperedBodyWrongSecretAndGarbage() {
        String header = WebhookSignature.sign(SECRET, NOW.getEpochSecond(), "{\"amount\":100}");
        assertThat(WebhookSignature.verify(SECRET, header, "{\"amount\":1}", NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify("another-secret-0123456789-abcdefghijklm", header, "{\"amount\":100}",
                NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, null, "{}", NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "t=abc,v1=00", "{}", NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, "v1=00", "{}", NOW, TOLERANCE)).isFalse();
    }

    @Test
    void rejectsReplayOutsideTolerance() {
        String old = WebhookSignature.sign(SECRET, NOW.minus(Duration.ofMinutes(6)).getEpochSecond(), "{}");
        String future = WebhookSignature.sign(SECRET, NOW.plus(Duration.ofMinutes(6)).getEpochSecond(), "{}");
        assertThat(WebhookSignature.verify(SECRET, old, "{}", NOW, TOLERANCE)).isFalse();
        assertThat(WebhookSignature.verify(SECRET, future, "{}", NOW, TOLERANCE)).isFalse();
    }
}
