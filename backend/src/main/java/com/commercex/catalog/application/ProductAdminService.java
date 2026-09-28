package com.commercex.catalog.application;

import com.commercex.catalog.application.CatalogViews.AdminProductSummary;
import com.commercex.catalog.application.CatalogViews.AdminProductView;
import com.commercex.catalog.application.CatalogViews.AdminVariantView;
import com.commercex.catalog.application.CatalogViews.ImageView;
import com.commercex.catalog.domain.Brand;
import com.commercex.catalog.domain.CatalogRuleViolation;
import com.commercex.catalog.domain.Product;
import com.commercex.catalog.domain.ProductDetails;
import com.commercex.catalog.domain.ProductStatus;
import com.commercex.catalog.domain.ProductVariant;
import com.commercex.catalog.domain.VariantSpec;
import com.commercex.catalog.infrastructure.BrandRepository;
import com.commercex.catalog.infrastructure.ProductRepository;
import com.commercex.catalog.infrastructure.ProductVariantRepository;
import com.commercex.category.CategoryApi;
import com.commercex.category.CategoryRef;
import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.persistence.Versions;
import com.commercex.common.text.Slugs;
import com.commercex.common.web.MoneyResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog management for ADMIN / CATALOG_MANAGER. */
@Service
public class ProductAdminService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final BrandRepository brands;
    private final CategoryApi categories;
    private final CatalogProperties properties;
    private final AuditLog auditLog;
    private final Clock clock;

    ProductAdminService(ProductRepository products, ProductVariantRepository variants, BrandRepository brands,
            CategoryApi categories, CatalogProperties properties, AuditLog auditLog, Clock clock) {
        this.products = products;
        this.variants = variants;
        this.brands = brands;
        this.categories = categories;
        this.properties = properties;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<AdminProductSummary> list(ProductStatus status, String nameQuery, int page, int size) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (nameQuery != null && !nameQuery.isBlank()) {
            String pattern = "%" + escapeLike(nameQuery.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        return products.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")))
                .map(p -> new AdminProductSummary(p.getId(), p.getSlug(), p.getName(), p.getStatus().name(),
                        MoneyResponse.of(p.getMinPriceMinor(), p.getCurrency()), p.activeVariants().size(),
                        p.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminProductView get(long id) {
        return view(load(id));
    }

    @Transactional
    public AdminProductView create(long actorId, ProductCommand command) {
        String slug = Slugs.resolve(command.slug(), command.name());
        if (products.existsBySlug(slug)) {
            throw new ConflictException("Slug '" + slug + "' is already in use");
        }
        Brand brand = brand(command.brandId());
        requireCategory(command.categoryId());
        requireSkusFree(command.variants(), null);

        Instant now = clock.instant();
        Product product = Product.create(properties.currency(), details(command, slug), brandName(brand), now);
        product.syncVariants(command.variants(), now);
        product = products.saveAndFlush(product);
        auditLog.record(actorId, "PRODUCT_CREATED", "product", product.getId(), Map.of("slug", slug));
        return view(product);
    }

    @Transactional
    public AdminProductView update(long actorId, long id, ProductCommand command) {
        Product product = load(id);
        Versions.require(product.getVersion(), command.version());
        String slug = Slugs.resolve(command.slug(), command.name());
        if (products.existsBySlugAndIdNot(slug, id)) {
            throw new ConflictException("Slug '" + slug + "' is already in use");
        }
        Brand brand = brand(command.brandId());
        requireCategory(command.categoryId());
        requireSkusFree(command.variants(), id);

        Instant now = clock.instant();
        product.updateDetails(details(command, slug), brandName(brand), now);
        product.syncVariants(command.variants(), now);
        products.flush();
        auditLog.record(actorId, "PRODUCT_UPDATED", "product", id, Map.of("slug", slug));
        return view(product);
    }

    @Transactional
    public AdminProductView publish(long actorId, long id) {
        Product product = load(id);
        boolean categoryActive = categories.findById(product.getCategoryId()).map(CategoryRef::active).orElse(false);
        product.publish(clock.instant(), categoryActive);
        products.flush();
        auditLog.record(actorId, "PRODUCT_PUBLISHED", "product", id, null);
        return view(product);
    }

    @Transactional
    public AdminProductView unpublish(long actorId, long id) {
        Product product = load(id);
        product.unpublish(clock.instant());
        products.flush();
        auditLog.record(actorId, "PRODUCT_UNPUBLISHED", "product", id, null);
        return view(product);
    }

    @Transactional
    public AdminProductView archive(long actorId, long id) {
        Product product = load(id);
        product.archive(clock.instant());
        products.flush();
        auditLog.record(actorId, "PRODUCT_ARCHIVED", "product", id, null);
        return view(product);
    }

    private Product load(long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private Brand brand(Long brandId) {
        if (brandId == null) {
            return null;
        }
        return brands.findById(brandId)
                .orElseThrow(() -> new CatalogRuleViolation("brandId", "brand " + brandId + " does not exist"));
    }

    private void requireCategory(long categoryId) {
        if (categories.findById(categoryId).isEmpty()) {
            throw new CatalogRuleViolation("categoryId", "category " + categoryId + " does not exist");
        }
    }

    /** SKUs are globally unique; a SKU of another product cannot be reused. */
    private void requireSkusFree(List<VariantSpec> specs, Long productId) {
        List<String> skus = specs.stream().map(VariantSpec::sku).toList();
        for (ProductVariant taken : variants.findBySkuIn(skus)) {
            if (!Objects.equals(taken.getProduct().getId(), productId)) {
                throw new ConflictException("SKU '" + taken.getSku() + "' is already used by another product");
            }
        }
    }

    private static ProductDetails details(ProductCommand c, String slug) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String tag : c.tags()) {
            tags.add(tag.trim().toLowerCase(Locale.ROOT));
        }
        List<String> options = new ArrayList<>();
        for (String option : c.options()) {
            options.add(option.trim());
        }
        return new ProductDetails(c.name().trim(), slug, blankToNull(c.shortDescription()),
                blankToNull(c.description()), c.brandId(), c.categoryId(), options, c.images(), tags);
    }

    static AdminProductView view(Product p) {
        return new AdminProductView(
                p.getId(), p.getSlug(), p.getName(), p.getShortDescription(), p.getDescription(), p.getBrandId(),
                p.getCategoryId(), p.getStatus().name(), MoneyResponse.of(p.getMinPriceMinor(), p.getCurrency()),
                p.getOptionNames(),
                p.getVariants().stream().map(v -> new AdminVariantView(v.getId(), v.getSku(), v.getOptionValues(),
                        MoneyResponse.of(v.getPriceMinor(), p.getCurrency()),
                        MoneyResponse.of(v.getCompareAtPriceMinor(), p.getCurrency()), v.isActive())).toList(),
                p.getImages().stream().map(ImageView::from).toList(),
                p.getTags().stream().sorted().toList(),
                p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
    }

    private static String brandName(Brand brand) {
        return brand == null ? null : brand.getName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
