package com.commercex.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "refunds")
public class Refund {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refunds_seq")
    @SequenceGenerator(name = "refunds_seq", sequenceName = "refunds_seq", allocationSize = 50)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private long paymentId;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(updatable = false)
    private String reason;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Refund() {
    }

    public static Refund start(long paymentId, String idempotencyKey, long amountMinor, String reason,
            Long actorUserId, Instant now) {
        Refund refund = new Refund();
        refund.paymentId = paymentId;
        refund.idempotencyKey = idempotencyKey;
        refund.amountMinor = amountMinor;
        refund.reason = reason;
        refund.actorUserId = actorUserId;
        refund.status = Status.PENDING;
        refund.createdAt = now;
        refund.updatedAt = now;
        return refund;
    }

    /** Returns true only on the transition PENDING → SUCCEEDED (so money is counted once). */
    public boolean succeed(String providerReference, Instant now) {
        if (status != Status.PENDING) {
            return false;
        }
        this.status = Status.SUCCEEDED;
        this.providerReference = providerReference;
        this.updatedAt = now;
        return true;
    }

    public void fail(String message, Instant now) {
        if (status == Status.PENDING) {
            this.status = Status.FAILED;
            this.failureMessage = message;
            this.updatedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public long getPaymentId() {
        return paymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Status getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
