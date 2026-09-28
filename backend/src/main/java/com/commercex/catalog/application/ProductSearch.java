package com.commercex.catalog.application;

import org.springframework.data.domain.Page;

/**
 * Read-side search port. Implemented with PostgreSQL full-text search today;
 * can be swapped for OpenSearch without touching callers.
 */
public interface ProductSearch {

    Page<ProductSummaryRow> search(ProductSearchCriteria criteria);
}
