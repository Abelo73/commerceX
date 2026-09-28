package com.commercex.catalog.infrastructure;

import com.commercex.catalog.domain.ProductVariant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findBySkuIn(Collection<String> skus);

    @Modifying
    @Query(value = "UPDATE product_variants SET in_stock = :inStock WHERE id = :variantId", nativeQuery = true)
    int updateInStock(long variantId, boolean inStock);

    /** Recomputes a product's flag from its active variants (by product ID, or via one of its variants). */
    @Modifying
    @Query(value = """
            UPDATE products p SET in_stock = EXISTS (
                SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.is_active AND v.in_stock)
            WHERE p.id = COALESCE(CAST(:productId AS BIGINT),
                                  (SELECT product_id FROM product_variants WHERE id = CAST(:variantId AS BIGINT)))
            """, nativeQuery = true)
    int refreshProductInStock(Long productId, Long variantId);
}
