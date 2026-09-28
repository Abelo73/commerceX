package com.commercex.catalog.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param currency ISO 4217 code for all product prices (single-currency store). */
@ConfigurationProperties("commercex.catalog")
public record CatalogProperties(String currency) {
}
