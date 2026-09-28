package com.commercex.catalog.application;

import com.commercex.catalog.domain.Brand;
import com.commercex.catalog.domain.ProductImage;
import com.commercex.category.CategoryRef;
import com.commercex.common.web.MoneyResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Read models returned by the catalog services. None of them are JPA entities. */
public final class CatalogViews {

    private CatalogViews() {
    }

    public record BrandView(long id, String name, String slug, String description, String logoUrl, boolean active,
            long version) {

        static BrandView from(Brand b) {
            return new BrandView(b.getId(), b.getName(), b.getSlug(), b.getDescription(), b.getLogoUrl(),
                    b.isActive(), b.getVersion());
        }
    }

    public record BrandRef(String name, String slug) {
    }

    public record CategorySummary(long id, String name, String slug) {

        static CategorySummary from(CategoryRef ref) {
            return ref == null ? null : new CategorySummary(ref.id(), ref.name(), ref.slug());
        }
    }

    public record ImageView(String url, String altText) {

        static ImageView from(ProductImage image) {
            return new ImageView(image.url(), image.altText());
        }
    }

    public record ProductSummaryView(
            long id,
            String slug,
            String name,
            String shortDescription,
            MoneyResponse price,
            MoneyResponse compareAtPrice,
            BigDecimal rating,
            int reviewCount,
            boolean inStock,
            BrandRef brand,
            CategorySummary category,
            ImageView image) {
    }

    public record OptionView(String name, List<String> values) {
    }

    /** {@code availability}: IN_STOCK | LOW_STOCK | OUT_OF_STOCK (exact counts are not exposed). */
    public record VariantView(long id, String sku, Map<String, String> options, String label, MoneyResponse price,
            MoneyResponse compareAtPrice, String availability) {
    }

    public record ProductDetailView(
            long id,
            String slug,
            String name,
            String shortDescription,
            String description,
            MoneyResponse price,
            MoneyResponse compareAtPrice,
            BigDecimal rating,
            int reviewCount,
            boolean inStock,
            BrandRef brand,
            CategorySummary category,
            List<OptionView> options,
            List<VariantView> variants,
            List<ImageView> images,
            List<String> tags,
            Instant publishedAt) {
    }

    public record AdminVariantView(long id, String sku, Map<String, String> options, MoneyResponse price,
            MoneyResponse compareAtPrice, boolean active) {
    }

    public record AdminProductView(
            long id,
            String slug,
            String name,
            String shortDescription,
            String description,
            Long brandId,
            long categoryId,
            String status,
            MoneyResponse price,
            List<String> options,
            List<AdminVariantView> variants,
            List<ImageView> images,
            List<String> tags,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    public record AdminProductSummary(long id, String slug, String name, String status, MoneyResponse price,
            int activeVariants, Instant updatedAt) {
    }
}
