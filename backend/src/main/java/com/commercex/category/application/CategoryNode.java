package com.commercex.category.application;

import java.util.List;

/** Public navigation tree node. */
public record CategoryNode(long id, String name, String slug, String description, List<CategoryNode> children) {
}
