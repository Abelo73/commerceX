package com.commercex.notification.application;

/** Plain-text email. Bodies may contain secrets (reset links), so {@code toString} omits them. */
public record EmailMessage(String to, String subject, String textBody) {

    @Override
    public String toString() {
        return "EmailMessage[subject=" + subject + "]";
    }
}
