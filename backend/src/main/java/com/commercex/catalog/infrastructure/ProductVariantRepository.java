package com.commercex.catalog.infrastructure;

import com.commercex.catalog.domain.ProductVariant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findBySkuIn(Collection<String> skus);
}
