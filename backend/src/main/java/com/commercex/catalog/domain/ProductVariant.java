package com.commercex.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_variants")
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_variants_seq")
    @SequenceGenerator(name = "product_variants_seq", sequenceName = "product_variants_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(nullable = false, updatable = false)
    private String sku;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_values", nullable = false)
    private Map<String, String> optionValues = new LinkedHashMap<>();

    @Column(name = "price_minor", nullable = false)
    private long priceMinor;

    @Column(name = "compare_at_price_minor")
    private Long compareAtPriceMinor;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ProductVariant() {
    }

    static ProductVariant create(Product product, VariantSpec spec, int position, Instant now) {
        ProductVariant variant = new ProductVariant();
        variant.product = product;
        variant.sku = spec.sku();
        variant.createdAt = now;
        variant.apply(spec, position, now);
        return variant;
    }

    void apply(VariantSpec spec, int position, Instant now) {
        this.optionValues = new LinkedHashMap<>(spec.optionValues());
        this.priceMinor = spec.priceMinor();
        this.compareAtPriceMinor = spec.compareAtPriceMinor();
        this.active = spec.active();
        this.position = position;
        this.updatedAt = now;
    }

    void deactivate(Instant now) {
        if (active) {
            active = false;
            updatedAt = now;
        }
    }

    /** Human-readable option label in the product's option order, e.g. {@code "Black / 42"}. */
    public String label(List<String> optionNames) {
        return optionNames.stream()
                .map(optionValues::get)
                .filter(v -> v != null)
                .collect(Collectors.joining(" / "));
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public Map<String, String> getOptionValues() {
        return Map.copyOf(optionValues);
    }

    public long getPriceMinor() {
        return priceMinor;
    }

    public Long getCompareAtPriceMinor() {
        return compareAtPriceMinor;
    }

    public boolean isActive() {
        return active;
    }

    public int getPosition() {
        return position;
    }
}
