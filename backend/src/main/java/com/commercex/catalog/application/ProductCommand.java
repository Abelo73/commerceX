package com.commercex.catalog.application;

import com.commercex.catalog.domain.ProductImage;
import com.commercex.catalog.domain.VariantSpec;
import java.util.List;

/**
 * Full desired state of a product (PUT semantics). {@code version} is required for updates.
 * {@code slug} is derived from the name when blank.
 */
public record ProductCommand(
        String name,
        String slug,
        String shortDescription,
        String description,
        Long brandId,
        long categoryId,
        List<String> options,
        List<ProductImage> images,
        List<String> tags,
        List<VariantSpec> variants,
        Long version) {
}
