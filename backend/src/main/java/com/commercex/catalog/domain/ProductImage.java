package com.commercex.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProductImage(
        @Column(name = "url", nullable = false) String url,
        @Column(name = "alt_text") String altText) {
}
