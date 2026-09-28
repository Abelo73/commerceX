package com.commercex.catalog.application;

import java.util.Locale;

public enum ProductSort {
    RELEVANCE,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    RATING;

    /** Accepts {@code price_asc}, {@code PRICE_ASC}, ...; null → default for the query. */
    public static ProductSort parse(String value, boolean hasQuery) {
        if (value == null || value.isBlank()) {
            return hasQuery ? RELEVANCE : NEWEST;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
