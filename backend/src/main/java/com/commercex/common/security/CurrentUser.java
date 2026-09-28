package com.commercex.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the authenticated user's ID from the access token ({@code sub} claim). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
