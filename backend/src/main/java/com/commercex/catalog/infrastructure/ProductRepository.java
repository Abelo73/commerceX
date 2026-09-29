package com.commercex.catalog.infrastructure;

import com.commercex.catalog.domain.Product;
import com.commercex.catalog.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, long id);

    boolean existsByBrandId(long brandId);

    long countByStatus(ProductStatus status);

    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);

    @Modifying
    @Query(value = "UPDATE products SET rating_avg = :average, review_count = :count WHERE id = :productId",
            nativeQuery = true)
    int updateRating(long productId, BigDecimal average, int count);
}
