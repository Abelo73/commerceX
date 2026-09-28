package com.commercex.catalog;

/** Compact product view (wishlist, recommendations). {@code priceMinor} is the "from" price. */
public record ProductCard(
        long productId,
        String slug,
        String name,
        Long priceMinor,
        String currency,
        String imageUrl,
        boolean inStock,
        boolean published) {
}
