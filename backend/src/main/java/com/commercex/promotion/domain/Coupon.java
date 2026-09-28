package com.commercex.promotion.domain;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "coupons_seq")
    @SequenceGenerator(name = "coupons_seq", sequenceName = "coupons_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private long value;

    @Column(name = "max_discount_minor")
    private Long maxDiscountMinor;

    @Column(name = "min_order_minor", nullable = false)
    private long minOrderMinor;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    /** Changed only by atomic SQL (redeem / release); read-only here so entity saves never overwrite it. */
    @Column(name = "used_count", insertable = false, updatable = false)
    private int usedCount;

    @Column(name = "per_customer_limit")
    private Integer perCustomerLimit;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @ElementCollection
    @CollectionTable(name = "coupon_products", joinColumns = @JoinColumn(name = "coupon_id"))
    @Column(name = "product_id", nullable = false)
    private Set<Long> productIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "coupon_categories", joinColumns = @JoinColumn(name = "coupon_id"))
    @Column(name = "category_id", nullable = false)
    private Set<Long> categoryIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Coupon() {
    }

    public record Definition(String description, CouponType type, long value, Long maxDiscountMinor,
            long minOrderMinor, Instant startsAt, Instant endsAt, Integer usageLimit, Integer perCustomerLimit,
            boolean active, Set<Long> productIds, Set<Long> categoryIds) {
    }

    public static Coupon create(String code, Definition d, Instant now) {
        Coupon coupon = new Coupon();
        coupon.code = code;
        coupon.createdAt = now;
        coupon.update(d, now);
        return coupon;
    }

    public void update(Definition d, Instant now) {
        this.description = d.description();
        this.type = d.type();
        this.value = d.value();
        this.maxDiscountMinor = d.maxDiscountMinor();
        this.minOrderMinor = d.minOrderMinor();
        this.startsAt = d.startsAt();
        this.endsAt = d.endsAt();
        this.usageLimit = d.usageLimit();
        this.perCustomerLimit = d.perCustomerLimit();
        this.active = d.active();
        this.productIds.clear();
        this.productIds.addAll(d.productIds());
        this.categoryIds.clear();
        this.categoryIds.addAll(d.categoryIds());
        this.updatedAt = now;
    }

    /** @param expandedCategoryIds restriction categories plus all their subcategories */
    public CouponRules rules(Set<Long> expandedCategoryIds) {
        return new CouponRules(active, startsAt, endsAt, type, value, maxDiscountMinor, minOrderMinor, usageLimit,
                usedCount, perCustomerLimit, Set.copyOf(productIds), expandedCategoryIds);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public CouponType getType() {
        return type;
    }

    public long getValue() {
        return value;
    }

    public Long getMaxDiscountMinor() {
        return maxDiscountMinor;
    }

    public long getMinOrderMinor() {
        return minOrderMinor;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public Integer getPerCustomerLimit() {
        return perCustomerLimit;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Long> getProductIds() {
        return Set.copyOf(productIds);
    }

    public Set<Long> getCategoryIds() {
        return Set.copyOf(categoryIds);
    }

    public long getVersion() {
        return version;
    }
}
