package com.commercex.catalog.api;

final class CatalogPatterns {

    static final String HTTP_URL = "^https?://[^\\s<>\"]+$";
    static final String SKU = "^[A-Za-z0-9][A-Za-z0-9._-]*$";
    static final String TAG = "^[A-Za-z0-9][A-Za-z0-9 -]*$";

    /** 1 billion major units: far above any real price, far below overflow. */
    static final long MAX_PRICE_MINOR = 100_000_000_000L;

    private CatalogPatterns() {
    }
}
