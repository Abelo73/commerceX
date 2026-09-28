-- CommerceX baseline: extensions and cross-cutting tables (see docs/erd.md).

CREATE EXTENSION IF NOT EXISTS citext;

-- Idempotency for order creation, payments and refunds.
CREATE SEQUENCE idempotency_keys_seq INCREMENT BY 50;

CREATE TABLE idempotency_keys (
    id              BIGINT       PRIMARY KEY DEFAULT nextval('idempotency_keys_seq'),
    idem_key        VARCHAR(255) NOT NULL,
    user_id         BIGINT       NOT NULL,
    operation       VARCHAR(64)  NOT NULL,
    request_hash    CHAR(64)     NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_status INT,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_keys UNIQUE (user_id, operation, idem_key)
);

CREATE INDEX ix_idempotency_keys_expires_at ON idempotency_keys (expires_at);

-- Security and administrative audit trail. Append-only.
CREATE SEQUENCE audit_log_seq INCREMENT BY 50;

CREATE TABLE audit_log (
    id             BIGINT       PRIMARY KEY DEFAULT nextval('audit_log_seq'),
    actor_user_id  BIGINT,
    action         VARCHAR(64)  NOT NULL,
    resource_type  VARCHAR(64),
    resource_id    VARCHAR(64),
    ip             VARCHAR(45),
    user_agent     VARCHAR(512),
    correlation_id VARCHAR(64),
    details        JSONB,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_log_actor_created ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_log_resource ON audit_log (resource_type, resource_id);
