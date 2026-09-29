package com.commercex.catalog.application;

import com.commercex.catalog.CatalogApi;
import com.commercex.catalog.ProductCard;
import com.commercex.catalog.VariantSnapshot;
import com.commercex.catalog.domain.Product;
import com.commercex.catalog.domain.ProductImage;
import com.commercex.catalog.domain.ProductStatus;
import com.commercex.catalog.domain.ProductVariant;
import com.commercex.catalog.infrastructure.ProductRepository;
import com.commercex.catalog.infrastructure.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogApiService implements CatalogApi {

    private final ProductVariantRepository variants;
    private final ProductRepository products;

    CatalogApiService(ProductVariantRepository variants, ProductRepository products) {
        this.variants = variants;
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, VariantSnapshot> variants(Collection<Long> variantIds) {
        Map<Long, VariantSnapshot> result = new HashMap<>();
        if (variantIds.isEmpty()) {
            return result;
        }
        for (ProductVariant v : variants.findWithProductByIdIn(variantIds)) {
            Product p = v.getProduct();
            result.put(v.getId(), new VariantSnapshot(
                    v.getId(), p.getId(), p.getCategoryId(), p.getSlug(), p.getName(), v.getSku(), v.label(p.getOptionNames()),
                    v.getPriceMinor(), v.getCompareAtPriceMinor(), p.getCurrency(), firstImage(p.getImages()),
                    p.getStatus() == ProductStatus.PUBLISHED && v.isActive()));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ProductCard> products(Collection<Long> productIds) {
        Map<Long, ProductCard> result = new HashMap<>();
        for (Product p : products.findAllById(productIds)) {
            result.put(p.getId(), new ProductCard(p.getId(), p.getSlug(), p.getName(), p.getMinPriceMinor(),
                    p.getCurrency(), firstImage(p.getImages()), p.isInStock(),
                    p.getStatus() == ProductStatus.PUBLISHED));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> publishedProductId(String slug) {
        return products.findBySlugAndStatus(slug, ProductStatus.PUBLISHED).map(Product::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public long publishedProductCount() {
        return products.countByStatus(ProductStatus.PUBLISHED);
    }

    @Override
    @Transactional
    public void updateRating(long productId, BigDecimal average, int reviewCount) {
        products.updateRating(productId, average, reviewCount);
    }

    private static String firstImage(List<ProductImage> images) {
        return images.isEmpty() ? null : images.get(0).url();
    }
}
