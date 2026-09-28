package com.commercex.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row of the append-only status history. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_status_history_seq")
    @SequenceGenerator(name = "order_status_history_seq", sequenceName = "order_status_history_seq",
            allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private OrderStatus toStatus;

    @Column(updatable = false)
    private String reason;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusChange() {
    }

    OrderStatusChange(Order order, OrderStatus from, OrderStatus to, String reason, Long actorUserId, Instant at) {
        this.order = order;
        this.fromStatus = from;
        this.toStatus = to;
        this.reason = reason;
        this.actorUserId = actorUserId;
        this.createdAt = at;
    }

    public OrderStatus getFromStatus() {
        return fromStatus;
    }

    public OrderStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
