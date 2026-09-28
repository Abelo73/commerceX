package com.commercex.catalog;

/**
 * Point-in-time view of a variant with its product. Prices are in minor units.
 *
 * @param purchasable product is PUBLISHED and the variant is active
 */
public record VariantSnapshot(
        long variantId,
        long productId,
        long categoryId,
        String productSlug,
        String productName,
        String sku,
        String label,
        long priceMinor,
        Long compareAtPriceMinor,
        String currency,
        String imageUrl,
        boolean purchasable) {
}
