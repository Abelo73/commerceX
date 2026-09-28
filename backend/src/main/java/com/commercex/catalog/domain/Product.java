package com.commercex.catalog.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Product aggregate: details, option names, images, tags and variants.
 * Variants are matched by SKU on update and never deleted, so their IDs stay stable for carts,
 * inventory and orders.
 */
@Entity
@Table(name = "products")
public class Product {

    public static final int MAX_OPTIONS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "products_seq")
    @SequenceGenerator(name = "products_seq", sequenceName = "products_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_description")
    private String shortDescription;

    private String description;

    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "category_id", nullable = false)
    private long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "min_price_minor")
    private Long minPriceMinor;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "search_keywords")
    private String searchKeywords;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @ElementCollection
    @CollectionTable(name = "product_options", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false)
    @BatchSize(size = 50)
    private List<String> optionNames = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "position")
    @BatchSize(size = 50)
    private List<ProductImage> images = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag", nullable = false)
    @BatchSize(size = 50)
    private Set<String> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("position ASC, id ASC")
    @BatchSize(size = 50)
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() {
    }

    public static Product create(String currency, ProductDetails details, String brandName, Instant now) {
        Product product = new Product();
        product.currency = currency;
        product.status = ProductStatus.DRAFT;
        product.createdAt = now;
        product.updateDetails(details, brandName, now);
        return product;
    }

    public void updateDetails(ProductDetails d, String brandName, Instant now) {
        if (d.optionNames().size() > MAX_OPTIONS) {
            throw new CatalogRuleViolation("options", "at most " + MAX_OPTIONS + " options are allowed");
        }
        if (new HashSet<>(d.optionNames()).size() != d.optionNames().size()) {
            throw new CatalogRuleViolation("options", "option names must be unique");
        }
        this.name = d.name();
        this.slug = d.slug();
        this.shortDescription = d.shortDescription();
        this.description = d.description();
        this.brandId = d.brandId();
        this.categoryId = d.categoryId();
        this.optionNames.clear();
        this.optionNames.addAll(d.optionNames());
        this.images.clear();
        this.images.addAll(d.images());
        this.tags.clear();
        this.tags.addAll(d.tags());
        this.searchKeywords = keywords(brandName, d.tags());
        this.updatedAt = now; // always dirties the root so the version increments
    }

    /**
     * Reconciles variants with the requested list: matching SKUs are updated, new SKUs are added,
     * and variants missing from the list are deactivated (never deleted).
     */
    public void syncVariants(List<VariantSpec> specs, Instant now) {
        Set<String> skus = new HashSet<>();
        Set<Map<String, String>> requestedCombinations = new HashSet<>();
        for (int i = 0; i < specs.size(); i++) {
            VariantSpec spec = specs.get(i);
            String field = "variants[" + i + "]";
            if (!skus.add(spec.sku())) {
                throw new CatalogRuleViolation(field + ".sku", "duplicate SKU " + spec.sku());
            }
            if (!spec.optionValues().keySet().equals(new HashSet<>(optionNames))) {
                throw new CatalogRuleViolation(field + ".options",
                        "must provide exactly one value for each option " + optionNames);
            }
            if (!requestedCombinations.add(spec.optionValues())) {
                throw new CatalogRuleViolation(field + ".options", "duplicate option combination "
                        + spec.optionValues());
            }
            if (spec.priceMinor() <= 0) {
                throw new CatalogRuleViolation(field + ".price", "must be greater than zero");
            }
            if (spec.compareAtPriceMinor() != null && spec.compareAtPriceMinor() <= spec.priceMinor()) {
                throw new CatalogRuleViolation(field + ".compareAtPrice", "must be greater than the price");
            }
        }

        Map<String, ProductVariant> existing = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getSku, Function.identity()));
        // A deactivated variant still owns its option combination (unique in the database).
        for (ProductVariant variant : variants) {
            if (!skus.contains(variant.getSku()) && requestedCombinations.contains(variant.getOptionValues())) {
                throw new CatalogRuleViolation("variants", "option combination " + variant.getOptionValues()
                        + " belongs to existing SKU " + variant.getSku() + "; reuse that SKU instead");
            }
        }

        for (int i = 0; i < specs.size(); i++) {
            VariantSpec spec = specs.get(i);
            ProductVariant variant = existing.get(spec.sku());
            if (variant == null) {
                variants.add(ProductVariant.create(this, spec, i, now));
            } else {
                variant.apply(spec, i, now);
            }
        }
        for (ProductVariant variant : variants) {
            if (!skus.contains(variant.getSku())) {
                variant.deactivate(now);
            }
        }
        OptionalLong minPrice = activeVariants().stream().mapToLong(ProductVariant::getPriceMinor).min();
        this.minPriceMinor = minPrice.isPresent() ? minPrice.getAsLong() : null;
        if (status == ProductStatus.PUBLISHED && minPriceMinor == null) {
            throw new CatalogRuleViolation("variants", "a published product needs at least one active variant");
        }
        this.updatedAt = now;
    }

    public void publish(Instant now, boolean categoryActive) {
        if (status == ProductStatus.PUBLISHED) {
            return;
        }
        if (activeVariants().isEmpty()) {
            throw new CatalogRuleViolation("variants", "at least one active variant is required to publish");
        }
        if (!categoryActive) {
            throw new CatalogRuleViolation("categoryId", "the product's category is inactive");
        }
        this.status = ProductStatus.PUBLISHED;
        if (publishedAt == null) {
            publishedAt = now;
        }
        this.updatedAt = now;
    }

    public void unpublish(Instant now) {
        this.status = ProductStatus.DRAFT;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        this.status = ProductStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public List<ProductVariant> activeVariants() {
        return variants.stream().filter(ProductVariant::isActive).toList();
    }

    /** The cheapest active variant: its price is the "from" price, its compare-at the strike-through price. */
    public ProductVariant cheapestActiveVariant() {
        return activeVariants().stream()
                .min(Comparator.comparingLong(ProductVariant::getPriceMinor).thenComparing(ProductVariant::getPosition))
                .orElse(null);
    }

    /** Distinct values per option, in variant order, considering active variants only. */
    public Map<String, List<String>> activeOptionValues() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String option : optionNames) {
            values.put(option, activeVariants().stream()
                    .map(v -> v.getOptionValues().get(option))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        }
        return values;
    }

    static String keywords(String brandName, Set<String> tags) {
        List<String> parts = new ArrayList<>();
        if (brandName != null) {
            parts.add(brandName);
        }
        parts.addAll(tags);
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public Long getBrandId() {
        return brandId;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getMinPriceMinor() {
        return minPriceMinor;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<String> getOptionNames() {
        return List.copyOf(optionNames);
    }

    public List<ProductImage> getImages() {
        return List.copyOf(images);
    }

    public Set<String> getTags() {
        return Set.copyOf(tags);
    }

    public List<ProductVariant> getVariants() {
        return List.copyOf(variants);
    }
}
