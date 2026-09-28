package com.commercex.category;

/** Read-only view of a category for other modules. */
public record CategoryRef(long id, String name, String slug, Long parentId, boolean active) {
}
