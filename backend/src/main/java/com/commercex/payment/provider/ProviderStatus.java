package com.commercex.payment.provider;

/** Provider-neutral payment state. */
public enum ProviderStatus {
    /** Accepted, outcome not known yet. */
    PENDING,
    /** Customer must complete an extra step (e.g. 3-D Secure) at {@code nextActionUrl}. */
    REQUIRES_ACTION,
    SUCCEEDED,
    FAILED
}
