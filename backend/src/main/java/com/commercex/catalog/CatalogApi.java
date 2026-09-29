package com.commercex.catalog;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Public API of the catalog module for cart, wishlist and checkout. */
public interface CatalogApi {

    /**
     * Current sellable data for each known variant. Unknown IDs are absent from the result.
     * Check {@link VariantSnapshot#purchasable()} before selling.
     */
    Map<Long, VariantSnapshot> variants(Collection<Long> variantIds);

    /** Card data for each known product (any status). Unknown IDs are absent from the result. */
    Map<Long, ProductCard> products(Collection<Long> productIds);

    /** Stores approved-review statistics (called by the review module inside its transaction). */
    void updateRating(long productId, BigDecimal average, int reviewCount);

    /** ID of the PUBLISHED product with this slug. */
    Optional<Long> publishedProductId(String slug);

    long publishedProductCount();
}
