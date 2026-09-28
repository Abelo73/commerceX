package com.commercex.catalog.domain;

import java.util.Map;

/** Desired state of one variant, identified by SKU. */
public record VariantSpec(String sku, Map<String, String> optionValues, long priceMinor, Long compareAtPriceMinor,
        boolean active) {
}
