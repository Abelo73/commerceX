package com.commercex.category.infrastructure;

import com.commercex.category.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, long id);

    boolean existsByParentId(long parentId);

    List<Category> findAllByOrderByPositionAscNameAsc();

    /** The category and all descendants (any active state). */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM categories WHERE id = :rootId
                UNION ALL
                SELECT c.id FROM categories c JOIN subtree s ON c.parent_id = s.id
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<Long> subtreeIds(long rootId);

    /** Active category plus descendants reachable through active categories only. */
    @Query(value = """
            WITH RECURSIVE subtree AS (
                SELECT id FROM categories WHERE slug = :slug AND is_active
                UNION ALL
                SELECT c.id FROM categories c JOIN subtree s ON c.parent_id = s.id WHERE c.is_active
            )
            SELECT id FROM subtree
            """, nativeQuery = true)
    List<Long> activeSubtreeIdsBySlug(String slug);

    /** Number of levels from this category up to its root (root = 1). */
    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT id, parent_id, 1 AS depth FROM categories WHERE id = :id
                UNION ALL
                SELECT c.id, c.parent_id, a.depth + 1 FROM categories c JOIN ancestors a ON c.id = a.parent_id
            )
            SELECT max(depth) FROM ancestors
            """, nativeQuery = true)
    int depthOf(long id);

    /** Number of levels below this category, including itself (leaf = 1). */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT id, 1 AS depth FROM categories WHERE id = :id
                UNION ALL
                SELECT c.id, d.depth + 1 FROM categories c JOIN descendants d ON c.parent_id = d.id
            )
            SELECT max(depth) FROM descendants
            """, nativeQuery = true)
    int heightOf(long id);
}
