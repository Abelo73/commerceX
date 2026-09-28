package com.commercex.catalog.infrastructure;

import com.commercex.catalog.domain.Product;
import com.commercex.catalog.domain.ProductStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, long id);

    boolean existsByBrandId(long brandId);

    Optional<Product> findBySlugAndStatus(String slug, ProductStatus status);
}
