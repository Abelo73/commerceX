package com.commercex.payment.provider;

public record RefundResult(String providerReference, Status status, String failureMessage) {

    public enum Status { PENDING, SUCCEEDED, FAILED }
}
