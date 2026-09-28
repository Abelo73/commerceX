-- Inventory module. One row per variant; a missing row means zero stock.
-- available = on_hand - reserved. The CHECK constraints make overselling impossible even if
-- application code is wrong: the database rejects any state with reserved > on_hand.

CREATE TABLE inventory_items (
    variant_id          BIGINT      PRIMARY KEY REFERENCES product_variants (id),
    on_hand             INT         NOT NULL DEFAULT 0,
    reserved            INT         NOT NULL DEFAULT 0,
    sold                BIGINT      NOT NULL DEFAULT 0,
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_inventory_on_hand_non_negative CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_reserved_within_on_hand CHECK (reserved <= on_hand),
    CONSTRAINT ck_inventory_threshold_non_negative CHECK (low_stock_threshold >= 0)
);

-- Low-stock report: available <= threshold.
CREATE INDEX ix_inventory_items_low_stock ON inventory_items ((on_hand - reserved - low_stock_threshold));

CREATE SEQUENCE inventory_reservations_seq INCREMENT BY 50;

-- Stock held for an order. Order FK is added with the orders table (Phase 6).
CREATE TABLE inventory_reservations (
    id         BIGINT      PRIMARY KEY DEFAULT nextval('inventory_reservations_seq'),
    order_id   BIGINT      NOT NULL,
    variant_id BIGINT      NOT NULL REFERENCES inventory_items (variant_id),
    quantity   INT         NOT NULL CHECK (quantity > 0),
    status     VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inventory_reservations_order_variant UNIQUE (order_id, variant_id)
);

CREATE INDEX ix_inventory_reservations_active_expiry ON inventory_reservations (expires_at) WHERE status = 'ACTIVE';

CREATE SEQUENCE inventory_movements_seq INCREMENT BY 50;

-- Append-only ledger of every stock change.
CREATE TABLE inventory_movements (
    id              BIGINT       PRIMARY KEY DEFAULT nextval('inventory_movements_seq'),
    variant_id      BIGINT       NOT NULL REFERENCES inventory_items (variant_id),
    type            VARCHAR(16)  NOT NULL CHECK (type IN ('RESTOCK', 'ADJUSTMENT', 'RETURN', 'RESERVE', 'RELEASE', 'COMMIT')),
    on_hand_delta   INT          NOT NULL,
    reserved_delta  INT          NOT NULL,
    on_hand_after   INT          NOT NULL,
    reserved_after  INT          NOT NULL,
    reason          VARCHAR(500),
    reference       VARCHAR(64),
    actor_user_id   BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_inventory_movements_variant_created ON inventory_movements (variant_id, created_at DESC, id DESC);
