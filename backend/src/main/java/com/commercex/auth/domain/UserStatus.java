package com.commercex.auth.domain;

public enum UserStatus {
    ACTIVE,
    /** Temporarily blocked by an administrator. */
    LOCKED,
    /** Permanently deactivated. */
    DISABLED
}
