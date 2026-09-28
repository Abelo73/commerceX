package com.commercex.category;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Public API of the category module. Other modules must not touch category tables directly. */
public interface CategoryApi {

    Optional<CategoryRef> findById(long id);

    Map<Long, CategoryRef> findByIds(Collection<Long> ids);

    /**
     * IDs of the active category with this slug and all its active descendants,
     * or an empty set when the slug is unknown or inactive.
     */
    Set<Long> activeSubtreeIds(String slug);
}
