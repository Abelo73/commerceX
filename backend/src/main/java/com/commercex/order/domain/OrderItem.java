package com.commercex.order.domain;

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

/** Immutable snapshot of a purchased line. */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_items_seq")
    @SequenceGenerator(name = "order_items_seq", sequenceName = "order_items_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(name = "variant_id", nullable = false, updatable = false)
    private long variantId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private long productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "variant_label", updatable = false)
    private String variantLabel;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(name = "image_url", updatable = false)
    private String imageUrl;

    @Column(name = "unit_price_minor", nullable = false, updatable = false)
    private long unitPriceMinor;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total_minor", nullable = false, updatable = false)
    private long lineTotalMinor;

    @Column(nullable = false, updatable = false)
    private int position;

    protected OrderItem() {
    }

    OrderItem(Order order, long variantId, long productId, String productName, String variantLabel, String sku,
            String imageUrl, long unitPriceMinor, int quantity, int position) {
        this.order = order;
        this.variantId = variantId;
        this.productId = productId;
        this.productName = productName;
        this.variantLabel = variantLabel;
        this.sku = sku;
        this.imageUrl = imageUrl;
        this.unitPriceMinor = unitPriceMinor;
        this.quantity = quantity;
        this.lineTotalMinor = Math.multiplyExact(unitPriceMinor, (long) quantity);
        this.position = position;
    }

    public long getVariantId() {
        return variantId;
    }

    public long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public String getSku() {
        return sku;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getUnitPriceMinor() {
        return unitPriceMinor;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLineTotalMinor() {
        return lineTotalMinor;
    }
}
