package com.commercex.catalog.domain;

import com.commercex.common.error.ValidationException;

/** A product/variant invariant was violated by the requested change. */
public class CatalogRuleViolation extends ValidationException {

    public CatalogRuleViolation(String field, String message) {
        super(field, message);
    }
}
