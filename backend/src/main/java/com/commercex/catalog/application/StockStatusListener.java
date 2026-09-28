package com.commercex.catalog.application;

import com.commercex.catalog.infrastructure.ProductVariantRepository;
import com.commercex.inventory.StockStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the catalog's in-stock flags in step with inventory. Runs synchronously inside the inventory
 * transaction, so the flag can never disagree with committed stock. Only fires when a variant crosses
 * zero availability, so the extra writes are rare.
 */
@Component
class StockStatusListener {

    private final ProductVariantRepository variants;

    StockStatusListener(ProductVariantRepository variants) {
        this.variants = variants;
    }

    @EventListener
    void on(StockStatusChanged event) {
        if (variants.updateInStock(event.variantId(), event.inStock()) > 0) {
            variants.refreshProductInStock(null, event.variantId());
        }
    }
}
