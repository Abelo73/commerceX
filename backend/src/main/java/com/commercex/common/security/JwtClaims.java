package com.commercex.common.security;

public final class JwtClaims {

    /** List of role names without the {@code ROLE_} prefix, e.g. {@code ["CUSTOMER"]}. */
    public static final String ROLES = "roles";

    private JwtClaims() {
    }
}
