package com.commercex.catalog.domain;

public enum ProductStatus {
    /** Being prepared; invisible to customers. */
    DRAFT,
    /** Visible and purchasable. */
    PUBLISHED,
    /** Retired; invisible, kept for order history. Can be restored to DRAFT. */
    ARCHIVED
}
