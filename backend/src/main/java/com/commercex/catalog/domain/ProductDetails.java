package com.commercex.catalog.domain;

import java.util.List;
import java.util.Set;

/** Editable product fields (everything except variants and lifecycle). */
public record ProductDetails(
        String name,
        String slug,
        String shortDescription,
        String description,
        Long brandId,
        long categoryId,
        List<String> optionNames,
        List<ProductImage> images,
        Set<String> tags) {
}
