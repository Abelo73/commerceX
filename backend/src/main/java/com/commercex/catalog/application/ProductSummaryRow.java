package com.commercex.catalog.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        boolean inStock,
        long categoryId,
        String brandName,
        String brandSlug,
        String imageUrl,
        String imageAlt,
        List<String> colors,
        Instant publishedAt) {
}
