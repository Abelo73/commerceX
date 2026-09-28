-- Order module. Order lines and the shipping address are snapshots: later catalog or address
-- changes never alter a placed order.

CREATE SEQUENCE orders_seq INCREMENT BY 50;
-- Human-facing order numbers: CX-10001, CX-10002, ...
CREATE SEQUENCE order_number_seq START WITH 10001;

CREATE TABLE orders (
    id               BIGINT       PRIMARY KEY,
    order_number     VARCHAR(20)  NOT NULL,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    customer_email   VARCHAR(254) NOT NULL,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'PROCESSING', 'PACKED',
                                     'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED', 'PAYMENT_FAILED')),
    currency         CHAR(3)      NOT NULL,
    subtotal_minor   BIGINT       NOT NULL CHECK (subtotal_minor >= 0),
    discount_minor   BIGINT       NOT NULL DEFAULT 0 CHECK (discount_minor >= 0),
    shipping_minor   BIGINT       NOT NULL CHECK (shipping_minor >= 0),
    tax_minor        BIGINT       NOT NULL DEFAULT 0 CHECK (tax_minor >= 0),
    total_minor      BIGINT       NOT NULL CHECK (total_minor >= 0),
    shipping_method  VARCHAR(32)  NOT NULL,
    shipping_address JSONB        NOT NULL,
    -- Unpaid orders are cancelled (and their stock released) after this instant.
    payment_due_at   TIMESTAMPTZ  NOT NULL,
    placed_at        TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT ck_orders_total CHECK (total_minor = subtotal_minor - discount_minor + shipping_minor + tax_minor)
);

CREATE INDEX ix_orders_user_placed ON orders (user_id, placed_at DESC);
CREATE INDEX ix_orders_status_placed ON orders (status, placed_at DESC);
CREATE INDEX ix_orders_unpaid_due ON orders (payment_due_at) WHERE status IN ('PENDING_PAYMENT', 'PAYMENT_FAILED');

CREATE SEQUENCE order_items_seq INCREMENT BY 50;

CREATE TABLE order_items (
    id               BIGINT        PRIMARY KEY,
    order_id         BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    variant_id       BIGINT        NOT NULL REFERENCES product_variants (id),
    product_id       BIGINT        NOT NULL,
    product_name     VARCHAR(200)  NOT NULL,
    variant_label    VARCHAR(200),
    sku              VARCHAR(64)   NOT NULL,
    image_url        VARCHAR(2048),
    unit_price_minor BIGINT        NOT NULL CHECK (unit_price_minor > 0),
    quantity         INT           NOT NULL CHECK (quantity > 0),
    line_total_minor BIGINT        NOT NULL,
    position         INT           NOT NULL,
    CONSTRAINT ck_order_items_line_total CHECK (line_total_minor = unit_price_minor * quantity)
);

CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE SEQUENCE order_status_history_seq INCREMENT BY 50;

-- Every status change, append-only (PRD §10: every status change is auditable).
CREATE TABLE order_status_history (
    id            BIGINT       PRIMARY KEY,
    order_id      BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20)  NOT NULL,
    reason        VARCHAR(500),
    actor_user_id BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_order_status_history_order ON order_status_history (order_id, created_at);

-- Reservations now reference real orders.
ALTER TABLE inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_order FOREIGN KEY (order_id) REFERENCES orders (id);
