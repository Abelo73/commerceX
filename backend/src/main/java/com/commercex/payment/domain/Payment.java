package com.commercex.payment.domain;

import com.commercex.payment.provider.PaymentResult;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments")
public class Payment {

    /** What applying a provider result changed. */
    public enum Change { NONE, SUCCEEDED, FAILED, REQUIRES_ACTION }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payments_seq")
    @SequenceGenerator(name = "payments_seq", sequenceName = "payments_seq", allocationSize = 50)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private long userId;

    @Column(nullable = false, updatable = false)
    private String provider;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "refunded_minor", nullable = false)
    private long refundedMinor;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "next_action_url")
    private String nextActionUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {
    }

    public static Payment start(long orderId, long userId, String provider, String idempotencyKey, long amountMinor,
            String currency, Instant now) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.provider = provider;
        payment.idempotencyKey = idempotencyKey;
        payment.amountMinor = amountMinor;
        payment.currency = currency;
        payment.status = PaymentStatus.PENDING;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /**
     * Applies a provider result. Settled payments (SUCCEEDED / FAILED) never change again, so duplicate
     * or out-of-order notifications are harmless.
     */
    public Change apply(PaymentResult result, Instant now) {
        if (providerReference == null && result.providerReference() != null) {
            providerReference = result.providerReference();
            updatedAt = now;
        }
        if (status.isSettled()) {
            return Change.NONE;
        }
        updatedAt = now;
        return switch (result.status()) {
            case SUCCEEDED -> {
                status = PaymentStatus.SUCCEEDED;
                nextActionUrl = null;
                yield Change.SUCCEEDED;
            }
            case FAILED -> {
                status = PaymentStatus.FAILED;
                failureCode = result.failureCode();
                failureMessage = result.failureMessage();
                nextActionUrl = null;
                yield Change.FAILED;
            }
            case REQUIRES_ACTION -> {
                boolean changed = status != PaymentStatus.REQUIRES_ACTION;
                status = PaymentStatus.REQUIRES_ACTION;
                nextActionUrl = result.nextActionUrl();
                yield changed ? Change.REQUIRES_ACTION : Change.NONE;
            }
            case PENDING -> Change.NONE;
        };
    }

    public long refundableMinor() {
        return status == PaymentStatus.SUCCEEDED ? amountMinor - refundedMinor : 0;
    }

    public void recordRefund(long amount, Instant now) {
        if (amount > refundableMinor()) {
            throw new IllegalStateException("Refund exceeds captured amount");
        }
        refundedMinor += amount;
        updatedAt = now;
    }

    public boolean isFullyRefunded() {
        return status == PaymentStatus.SUCCEEDED && refundedMinor == amountMinor;
    }

    public Long getId() {
        return id;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public long getRefundedMinor() {
        return refundedMinor;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public String getNextActionUrl() {
        return nextActionUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
