package com.commercex.order.domain;

import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import com.commercex.order.OrderAddress;
import com.commercex.order.PlaceOrderCommand;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq", sequenceName = "orders_seq", allocationSize = 50)
    private Long id;

    @Column(name = "order_number", nullable = false, updatable = false)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(name = "customer_email", nullable = false, updatable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "subtotal_minor", nullable = false, updatable = false)
    private long subtotalMinor;

    @Column(name = "discount_minor", nullable = false, updatable = false)
    private long discountMinor;

    @Column(name = "shipping_minor", nullable = false, updatable = false)
    private long shippingMinor;

    @Column(name = "tax_minor", nullable = false, updatable = false)
    private long taxMinor;

    @Column(name = "total_minor", nullable = false, updatable = false)
    private long totalMinor;

    @Column(name = "shipping_method", nullable = false, updatable = false)
    private String shippingMethod;

    @Column(name = "coupon_code", updatable = false)
    private String couponCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, updatable = false)
    private OrderAddress shippingAddress;

    @Column(name = "payment_due_at", nullable = false)
    private Instant paymentDueAt;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("createdAt ASC, id ASC")
    @BatchSize(size = 50)
    private List<OrderStatusChange> history = new ArrayList<>();

    protected Order() {
    }

    public static Order place(String orderNumber, PlaceOrderCommand c, Instant now, Instant paymentDueAt) {
        if (c.lines().isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        long computedSubtotal = c.lines().stream()
                .mapToLong(l -> Math.multiplyExact(l.unitPriceMinor(), (long) l.quantity())).sum();
        if (computedSubtotal != c.subtotalMinor()
                || c.totalMinor() != c.subtotalMinor() - c.discountMinor() + c.shippingMinor() + c.taxMinor()) {
            throw new IllegalArgumentException("Order totals are inconsistent");
        }
        Order order = new Order();
        order.orderNumber = orderNumber;
        order.userId = c.userId();
        order.customerEmail = c.customerEmail();
        order.status = OrderStatus.PENDING_PAYMENT;
        order.currency = c.currency();
        order.subtotalMinor = c.subtotalMinor();
        order.discountMinor = c.discountMinor();
        order.shippingMinor = c.shippingMinor();
        order.taxMinor = c.taxMinor();
        order.totalMinor = c.totalMinor();
        order.shippingMethod = c.shippingMethod();
        order.couponCode = c.couponCode();
        order.shippingAddress = c.shippingAddress();
        order.paymentDueAt = paymentDueAt;
        order.placedAt = now;
        order.createdAt = now;
        order.updatedAt = now;
        for (int i = 0; i < c.lines().size(); i++) {
            PlaceOrderCommand.Line l = c.lines().get(i);
            order.items.add(new OrderItem(order, l.variantId(), l.productId(), l.productName(), l.variantLabel(),
                    l.sku(), l.imageUrl(), l.unitPriceMinor(), l.quantity(), i));
        }
        order.history.add(new OrderStatusChange(order, null, OrderStatus.PENDING_PAYMENT, "Order placed",
                c.userId(), now));
        return order;
    }

    /** The only way to change status. Records history; side effects are applied by the application service. */
    public void transitionTo(OrderStatus target, String reason, Long actorUserId, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Order " + orderNumber + " cannot go from " + status + " to " + target,
                    Map.of("from", status.name(), "to", target.name()));
        }
        history.add(new OrderStatusChange(this, status, target, reason, actorUserId, now));
        this.status = target;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public long getUserId() {
        return userId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getSubtotalMinor() {
        return subtotalMinor;
    }

    public long getDiscountMinor() {
        return discountMinor;
    }

    public long getShippingMinor() {
        return shippingMinor;
    }

    public long getTaxMinor() {
        return taxMinor;
    }

    public long getTotalMinor() {
        return totalMinor;
    }

    public String getShippingMethod() {
        return shippingMethod;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public OrderAddress getShippingAddress() {
        return shippingAddress;
    }

    public Instant getPaymentDueAt() {
        return paymentDueAt;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public List<OrderStatusChange> getHistory() {
        return List.copyOf(history);
    }
}
