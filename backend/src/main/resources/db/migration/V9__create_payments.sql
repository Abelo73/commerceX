-- Payment module. No card data is ever stored: only opaque provider references.

CREATE SEQUENCE payments_seq INCREMENT BY 50;

CREATE TABLE payments (
    id                 BIGINT       PRIMARY KEY,
    order_id           BIGINT       NOT NULL REFERENCES orders (id),
    user_id            BIGINT       NOT NULL REFERENCES users (id),
    provider           VARCHAR(32)  NOT NULL,
    provider_reference VARCHAR(128),
    -- Client Idempotency-Key: a retried request finds this payment instead of creating another.
    idempotency_key    VARCHAR(255) NOT NULL,
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'REQUIRES_ACTION', 'SUCCEEDED', 'FAILED')),
    amount_minor       BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency           CHAR(3)      NOT NULL,
    refunded_minor     BIGINT       NOT NULL DEFAULT 0,
    failure_code       VARCHAR(64),
    failure_message    VARCHAR(500),
    next_action_url    VARCHAR(2048),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_payments_order_key UNIQUE (order_id, idempotency_key),
    CONSTRAINT ck_payments_refund_bounds CHECK (refunded_minor >= 0 AND refunded_minor <= amount_minor)
);

CREATE UNIQUE INDEX uq_payments_provider_reference ON payments (provider, provider_reference)
    WHERE provider_reference IS NOT NULL;
-- At most one in-flight or successful payment per order: a second checkout tab cannot charge twice.
CREATE UNIQUE INDEX uq_payments_one_active_per_order ON payments (order_id)
    WHERE status IN ('PENDING', 'REQUIRES_ACTION', 'SUCCEEDED');
CREATE INDEX ix_payments_unsettled ON payments (updated_at) WHERE status IN ('PENDING', 'REQUIRES_ACTION');

CREATE SEQUENCE refunds_seq INCREMENT BY 50;

CREATE TABLE refunds (
    id                 BIGINT       PRIMARY KEY,
    payment_id         BIGINT       NOT NULL REFERENCES payments (id),
    idempotency_key    VARCHAR(255) NOT NULL,
    provider_reference VARCHAR(128),
    amount_minor       BIGINT       NOT NULL CHECK (amount_minor > 0),
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    reason             VARCHAR(500),
    actor_user_id      BIGINT,
    failure_message    VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_refunds_payment_key UNIQUE (payment_id, idempotency_key)
);

CREATE INDEX ix_refunds_pending ON refunds (updated_at) WHERE status = 'PENDING';

CREATE SEQUENCE payment_webhook_events_seq INCREMENT BY 50;

-- Deduplication of provider webhooks (at-least-once delivery → exactly-once processing).
CREATE TABLE payment_webhook_events (
    id                BIGINT       PRIMARY KEY DEFAULT nextval('payment_webhook_events_seq'),
    provider          VARCHAR(32)  NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type        VARCHAR(64)  NOT NULL,
    payload           JSONB        NOT NULL,
    outcome           VARCHAR(32)  NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_webhook_events UNIQUE (provider, provider_event_id)
);
