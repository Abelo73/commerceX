package com.commercex.category.application;

import com.commercex.category.domain.Category;

public record CategoryView(long id, Long parentId, String name, String slug, String description, int position,
        boolean active, long version) {

    static CategoryView from(Category c) {
        return new CategoryView(c.getId(), c.getParentId(), c.getName(), c.getSlug(), c.getDescription(),
                c.getPosition(), c.isActive(), c.getVersion());
    }
}
