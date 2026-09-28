package com.commercex.auth.application;

import java.util.Locale;

final class Emails {

    private Emails() {
    }

    static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
