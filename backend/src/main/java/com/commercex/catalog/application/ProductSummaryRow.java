package com.commercex.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the public listing, straight from the search query. */
public record ProductSummaryRow(
        long id,
        String slug,
        String name,
        String shortDescription,
        String currency,
        long priceMinor,
        Long compareAtPriceMinor,
        BigDecimal ratingAvg,
        int reviewCount,
        long categoryId,
        String brandName,
        String brandSlug,
        String imageUrl,
        String imageAlt,
        Instant publishedAt) {
}
