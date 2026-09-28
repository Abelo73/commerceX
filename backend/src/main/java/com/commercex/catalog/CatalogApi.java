package com.commercex.catalog;

import java.util.Collection;
import java.util.Map;

/** Public API of the catalog module for cart, wishlist and checkout. */
public interface CatalogApi {

    /**
     * Current sellable data for each known variant. Unknown IDs are absent from the result.
     * Check {@link VariantSnapshot#purchasable()} before selling.
     */
    Map<Long, VariantSnapshot> variants(Collection<Long> variantIds);

    /** Card data for each known product (any status). Unknown IDs are absent from the result. */
    Map<Long, ProductCard> products(Collection<Long> productIds);
}
