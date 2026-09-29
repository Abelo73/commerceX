package com.commercex.order.application;

import com.commercex.common.audit.AuditLog;
import com.commercex.common.error.ConflictException;
import com.commercex.common.error.ErrorCode;
import com.commercex.common.error.NotFoundException;
import com.commercex.common.error.ValidationException;
import com.commercex.common.web.MoneyResponse;
import com.commercex.inventory.InventoryApi;
import com.commercex.inventory.ReservationLine;
import com.commercex.order.OrderApi;
import com.commercex.order.OrderDetails;
import com.commercex.order.OrderPlaced;
import com.commercex.order.OrderRef;
import com.commercex.order.OrderStats;
import com.commercex.order.OrderStatusChanged;
import com.commercex.order.PlaceOrderCommand;
import com.commercex.order.domain.Order;
import com.commercex.order.domain.OrderStatus;
import com.commercex.order.infrastructure.OrderRepository;
import com.commercex.order.infrastructure.OrderStatsQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService implements OrderApi {

    public record OrderSummary(long id, String orderNumber, String status, MoneyResponse total, int itemCount,
            String customerEmail, Instant placedAt) {
    }

    private final OrderRepository orders;
    private final OrderStatsQuery statsQuery;
    private final String currency;
    private final InventoryApi inventory;
    private final OrderProperties properties;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;
    private final Clock clock;

    OrderService(OrderRepository orders, OrderStatsQuery statsQuery, InventoryApi inventory,
            OrderProperties properties, ApplicationEventPublisher events, AuditLog auditLog, Clock clock,
            @Value("${commercex.catalog.currency}") String currency) {
        this.orders = orders;
        this.statsQuery = statsQuery;
        this.currency = currency;
        this.inventory = inventory;
        this.properties = properties;
        this.events = events;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    // ---- OrderApi --------------------------------------------------------------------------------

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OrderDetails place(PlaceOrderCommand command) {
        Instant now = clock.instant();
        Order order = Order.place(orders.nextOrderNumber(), command, now, now.plus(properties.paymentWindow()));
        order = orders.saveAndFlush(order); // the row must exist before reservations reference it
        inventory.reserve(order.getId(),
                command.lines().stream().map(l -> new ReservationLine(l.variantId(), l.quantity())).toList(),
                order.getPaymentDueAt());
        events.publishEvent(new OrderPlaced(order.getId(), order.getOrderNumber(), order.getUserId(),
                order.getTotalMinor(), order.getCurrency()));
        return details(order);
    }

    @Override
    @Transactional
    public boolean markPaid(long orderId, String paymentReference) {
        Order order = lock(orderId);
        if (order.getStatus().isPaidOrLater() || order.getStatus() == OrderStatus.REFUNDED) {
            return true; // duplicate payment notification
        }
        if (!order.getStatus().isUnpaid()) {
            return false; // e.g. cancelled after the payment window closed: caller refunds
        }
        transition(order, OrderStatus.PAID, "Payment captured (" + paymentReference + ")", null);
        return true;
    }

    @Override
    @Transactional
    public void markPaymentFailed(long orderId, String reason) {
        Order order = lock(orderId);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            transition(order, OrderStatus.PAYMENT_FAILED, reason, null);
        }
    }

    @Override
    @Transactional
    public boolean markRefunded(long orderId, String reason) {
        Order order = lock(orderId);
        if (!order.getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            return false;
        }
        transition(order, OrderStatus.REFUNDED, reason, null);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDetails> find(long orderId) {
        return orders.findById(orderId).map(OrderService::details);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> deliveredOrderContaining(long userId, long productId) {
        return orders.findDeliveredOrderIdsContaining(userId, productId).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStats stats(Instant from) {
        return statsQuery.stats(from, currency);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderRef> ref(long orderId) {
        return orders.findById(orderId).map(OrderService::ref);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderRef> refForCustomer(long userId, String orderNumber) {
        return orders.findByOrderNumberAndUserId(orderNumber, userId).map(OrderService::ref);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderRef> refByNumber(String orderNumber) {
        return orders.findByOrderNumber(orderNumber).map(OrderService::ref);
    }

    private static OrderRef ref(Order o) {
        return new OrderRef(o.getId(), o.getOrderNumber(), o.getUserId(), o.getStatus().name(), o.getTotalMinor(),
                o.getCurrency(), o.getPaymentDueAt());
    }

    // ---- customer --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<OrderSummary> customerOrders(long userId, int page, int size) {
        return orders.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "placedAt", "id")))
                .map(OrderService::summary);
    }

    /** Another customer's order is indistinguishable from a missing one. */
    @Transactional(readOnly = true)
    public OrderDetails customerOrder(long userId, String orderNumber) {
        return details(orders.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber)));
    }

    @Transactional
    public OrderDetails cancelByCustomer(long userId, String orderNumber) {
        Order owned = orders.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));
        Order order = lock(owned.getId());
        if (!order.getStatus().isUnpaid()) {
            throw new ConflictException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only unpaid orders can be cancelled online; contact support for a refund",
                    Map.of("status", order.getStatus().name()));
        }
        transition(order, OrderStatus.CANCELLED, "Cancelled by customer", userId);
        return details(order);
    }

    // ---- staff -----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<OrderSummary> staffOrders(OrderStatus status, String query, int page, int size) {
        Specification<Order> spec = (root, q, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        if (query != null && !query.isBlank()) {
            String term = query.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.equal(cb.lower(root.get("orderNumber")), term),
                    cb.equal(cb.lower(root.get("customerEmail")), term)));
        }
        return orders.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "placedAt", "id")))
                .map(OrderService::summary);
    }

    @Transactional(readOnly = true)
    public OrderDetails staffOrder(String orderNumber) {
        return details(orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber)));
    }

    @Transactional
    public OrderDetails transitionByStaff(long actorId, String orderNumber, OrderStatus target, String reason) {
        if (!OrderStatus.STAFF_TARGETS.contains(target)) {
            throw new ValidationException("toStatus", target + " is set by the payment system, not by staff");
        }
        Order found = orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));
        Order order = lock(found.getId());
        transition(order, target, reason, actorId);
        auditLog.record(actorId, "ORDER_STATUS_CHANGED", "order", order.getId(),
                Map.of("orderNumber", orderNumber, "to", target.name()));
        return details(order);
    }

    // ---- expiry ----------------------------------------------------------------------------------

    /** Cancels one overdue unpaid order (if any) and releases its stock. Returns whether one was found. */
    @Transactional
    public boolean cancelNextOverdueOrder() {
        Optional<Long> id = orders.lockNextOverdueUnpaid();
        if (id.isEmpty()) {
            return false;
        }
        Order order = lock(id.get());
        transition(order, OrderStatus.CANCELLED, "Payment window expired", null);
        return true;
    }

    // ---- internals -------------------------------------------------------------------------------

    /**
     * Applies a transition and its stock side effect in the current transaction:
     * PAID commits the reservation, cancelling an unpaid order releases it.
     */
    private void transition(Order order, OrderStatus target, String reason, Long actorId) {
        OrderStatus from = order.getStatus();
        order.transitionTo(target, reason, actorId, clock.instant());
        if (target == OrderStatus.PAID) {
            inventory.commit(order.getId());
        } else if (target == OrderStatus.CANCELLED && from.isUnpaid()) {
            inventory.release(order.getId(), reason);
        }
        orders.flush();
        events.publishEvent(new OrderStatusChanged(order.getId(), order.getOrderNumber(), order.getUserId(),
                from.name(), target.name(), reason));
    }

    private Order lock(long orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    static OrderDetails details(Order o) {
        String c = o.getCurrency();
        return new OrderDetails(
                o.getId(), o.getOrderNumber(), o.getStatus().name(),
                o.getItems().stream().map(i -> new OrderDetails.Item(i.getVariantId(), i.getProductId(),
                        i.getProductName(), i.getVariantLabel(), i.getSku(), i.getImageUrl(),
                        MoneyResponse.of(i.getUnitPriceMinor(), c), i.getQuantity(),
                        MoneyResponse.of(i.getLineTotalMinor(), c))).toList(),
                MoneyResponse.of(o.getSubtotalMinor(), c), MoneyResponse.of(o.getDiscountMinor(), c),
                MoneyResponse.of(o.getShippingMinor(), c), MoneyResponse.of(o.getTaxMinor(), c),
                MoneyResponse.of(o.getTotalMinor(), c),
                o.getCouponCode(),
                o.getShippingMethod(), o.getShippingAddress(), o.getCustomerEmail(), o.getPlacedAt(),
                o.getPaymentDueAt(),
                o.getHistory().stream().map(h -> new OrderDetails.StatusChange(
                        h.getFromStatus() == null ? null : h.getFromStatus().name(), h.getToStatus().name(),
                        h.getReason(), h.getCreatedAt())).toList());
    }

    private static OrderSummary summary(Order o) {
        return new OrderSummary(o.getId(), o.getOrderNumber(), o.getStatus().name(),
                MoneyResponse.of(o.getTotalMinor(), o.getCurrency()),
                o.getItems().stream().mapToInt(i -> i.getQuantity()).sum(), o.getCustomerEmail(), o.getPlacedAt());
    }
}
