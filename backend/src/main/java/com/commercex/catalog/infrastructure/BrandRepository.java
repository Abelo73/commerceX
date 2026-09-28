package com.commercex.catalog.infrastructure;

import com.commercex.catalog.domain.Brand;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, long id);

    List<Brand> findAllByOrderByNameAsc();

    List<Brand> findAllByActiveTrueOrderByNameAsc();

    /** Keeps product search keywords in sync after a brand rename. */
    @Modifying
    @Query(value = """
            UPDATE products p
            SET search_keywords = nullif(concat_ws(' ', :brandName,
                    (SELECT string_agg(t.tag, ' ') FROM product_tags t WHERE t.product_id = p.id)), '')
            WHERE p.brand_id = :brandId
            """, nativeQuery = true)
    int refreshProductKeywords(long brandId, String brandName);
}
