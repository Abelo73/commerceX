package com.commercex.category.application;

import com.commercex.category.CategoryApi;
import com.commercex.category.CategoryRef;
import com.commercex.category.domain.Category;
import com.commercex.category.infrastructure.CategoryRepository;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.common.persistence.Versions;
import com.commercex.common.text.Slugs;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService implements CategoryApi {

    private final CategoryRepository categories;
    private final AuditLog auditLog;
    private final Clock clock;

    CategoryService(CategoryRepository categories, AuditLog auditLog, Clock clock) {
        this.categories = categories;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // ---- public read side --------------------------------------------------------------------

    /** Active categories as a tree; a category is hidden when any ancestor is inactive. */
    @Transactional(readOnly = true)
    public List<CategoryNode> activeTree() {
        List<Category> all = categories.findAllByOrderByPositionAscNameAsc();
        Map<Long, List<Category>> byParent = new LinkedHashMap<>();
        for (Category c : all) {
            if (c.isActive()) {
                byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        return children(null, byParent);
    }

    private static List<CategoryNode> children(Long parentId, Map<Long, List<Category>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .map(c -> new CategoryNode(c.getId(), c.getName(), c.getSlug(), c.getDescription(),
                        children(c.getId(), byParent)))
                .toList();
    }

    // ---- CategoryApi (for other modules) -----------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoryRef> findById(long id) {
        return categories.findById(id).map(CategoryService::ref);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CategoryRef> findByIds(Collection<Long> ids) {
        return categories.findAllById(ids).stream()
                .collect(Collectors.toMap(Category::getId, CategoryService::ref));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> activeSubtreeIds(String slug) {
        return new HashSet<>(categories.activeSubtreeIdsBySlug(slug));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> subtreeIds(long categoryId) {
        return new HashSet<>(categories.subtreeIds(categoryId));
    }

    // ---- admin -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CategoryView> listAll() {
        return categories.findAllByOrderByPositionAscNameAsc().stream().map(CategoryView::from).toList();
    }

    @Transactional
    public CategoryView create(long actorId, CategoryCommand command) {
        String slug = slug(command);
        if (categories.existsBySlug(slug)) {
            throw slugTaken(slug);
        }
        if (command.parentId() != null) {
            requireExists(command.parentId(), "parentId");
            if (categories.depthOf(command.parentId()) + 1 > Category.MAX_DEPTH) {
                throw new ValidationException("parentId", "categories can be nested at most "
                        + Category.MAX_DEPTH + " levels deep");
            }
        }
        Category category = categories.save(Category.create(command.parentId(), command.name().trim(), slug,
                trimToNull(command.description()), command.position(), command.active(), clock.instant()));
        auditLog.record(actorId, "CATEGORY_CREATED", "category", category.getId(), Map.of("slug", slug));
        return CategoryView.from(category);
    }

    @Transactional
    public CategoryView update(long actorId, long id, CategoryCommand command) {
        Category category = load(id);
        Versions.require(category.getVersion(), command.version());
        String slug = slug(command);
        if (categories.existsBySlugAndIdNot(slug, id)) {
            throw slugTaken(slug);
        }
        if (!Objects.equals(command.parentId(), category.getParentId()) && command.parentId() != null) {
            requireExists(command.parentId(), "parentId");
            if (categories.subtreeIds(id).contains(command.parentId())) {
                throw new ValidationException("parentId", "a category cannot be moved under itself or its descendants");
            }
            if (categories.depthOf(command.parentId()) + categories.heightOf(id) > Category.MAX_DEPTH) {
                throw new ValidationException("parentId", "categories can be nested at most "
                        + Category.MAX_DEPTH + " levels deep");
            }
        }
        Instant now = clock.instant();
        category.update(command.parentId(), command.name().trim(), slug, trimToNull(command.description()),
                command.position(), command.active(), now);
        categories.flush();
        auditLog.record(actorId, "CATEGORY_UPDATED", "category", id, Map.of("slug", slug));
        return CategoryView.from(category);
    }

    /** Only empty leaf categories can be deleted; otherwise deactivate them. */
    @Transactional
    public void delete(long actorId, long id) {
        Category category = load(id);
        if (categories.existsByParentId(id)) {
            throw new ConflictException("Category has subcategories; move or delete them first, or deactivate it");
        }
        categories.delete(category);
        categories.flush(); // surface FK violations (products still in category) inside this call
        auditLog.record(actorId, "CATEGORY_DELETED", "category", id, Map.of("slug", category.getSlug()));
    }

    private Category load(long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    private void requireExists(long id, String field) {
        if (!categories.existsById(id)) {
            throw new ValidationException(field, "category " + id + " does not exist");
        }
    }

    private static String slug(CategoryCommand command) {
        return Slugs.resolve(command.slug(), command.name());
    }

    private static ConflictException slugTaken(String slug) {
        return new ConflictException("Slug '" + slug + "' is already in use");
    }

    private static CategoryRef ref(Category c) {
        return new CategoryRef(c.getId(), c.getName(), c.getSlug(), c.getParentId(), c.isActive());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
