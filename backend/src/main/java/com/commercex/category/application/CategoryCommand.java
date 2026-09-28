package com.commercex.category.application;

/** {@code slug} is derived from {@code name} when blank. {@code version} is required for updates. */
public record CategoryCommand(Long parentId, String name, String slug, String description, int position,
        boolean active, Long version) {
}
