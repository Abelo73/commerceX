package com.commercex.payment.domain;

public enum PaymentStatus {
    PENDING,
    REQUIRES_ACTION,
    SUCCEEDED,
    FAILED;

    public boolean isSettled() {
        return this == SUCCEEDED || this == FAILED;
    }
}
