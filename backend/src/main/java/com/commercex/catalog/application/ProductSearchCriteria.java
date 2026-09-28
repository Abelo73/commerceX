package com.commercex.catalog.application;

import java.util.List;
import java.util.Set;

/**
 * Public product listing filters. {@code categoryIds == null} means "any category";
 * an empty set means "the requested category does not exist" (no results).
 */
public record ProductSearchCriteria(
        String query,
        Set<Long> categoryIds,
        List<String> brandSlugs,
        Long minPriceMinor,
        Long maxPriceMinor,
        Double minRating,
        boolean inStockOnly,
        ProductSort sort,
        int page,
        int size) {
}
