-- Promotion module: coupons, restrictions, redemptions.

CREATE SEQUENCE coupons_seq INCREMENT BY 50;

CREATE TABLE coupons (
    id                 BIGINT       PRIMARY KEY,
    code               VARCHAR(32)  NOT NULL,
    description        VARCHAR(500),
    type               VARCHAR(16)  NOT NULL CHECK (type IN ('PERCENTAGE', 'FIXED')),
    -- PERCENTAGE: basis points (1250 = 12.5%). FIXED: minor units.
    value              BIGINT       NOT NULL CHECK (value > 0),
    max_discount_minor BIGINT       CHECK (max_discount_minor IS NULL OR max_discount_minor > 0),
    min_order_minor    BIGINT       NOT NULL DEFAULT 0 CHECK (min_order_minor >= 0),
    starts_at          TIMESTAMPTZ  NOT NULL,
    ends_at            TIMESTAMPTZ,
    usage_limit        INT          CHECK (usage_limit IS NULL OR usage_limit > 0),
    used_count         INT          NOT NULL DEFAULT 0,
    per_customer_limit INT          CHECK (per_customer_limit IS NULL OR per_customer_limit > 0),
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_code_format CHECK (code ~ '^[A-Z0-9_-]{3,32}$'),
    CONSTRAINT ck_coupons_percentage CHECK (type <> 'PERCENTAGE' OR value <= 10000),
    CONSTRAINT ck_coupons_window CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_coupons_usage CHECK (used_count >= 0 AND (usage_limit IS NULL OR used_count <= usage_limit))
);

-- Restrictions: when present, only matching lines are discounted.
CREATE TABLE coupon_products (
    coupon_id  BIGINT NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products (id),
    PRIMARY KEY (coupon_id, product_id)
);

CREATE TABLE coupon_categories (
    coupon_id   BIGINT NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories (id),
    PRIMARY KEY (coupon_id, category_id)
);

CREATE SEQUENCE coupon_redemptions_seq INCREMENT BY 50;

CREATE TABLE coupon_redemptions (
    id             BIGINT      PRIMARY KEY DEFAULT nextval('coupon_redemptions_seq'),
    coupon_id      BIGINT      NOT NULL REFERENCES coupons (id),
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    order_id       BIGINT      NOT NULL REFERENCES orders (id),
    discount_minor BIGINT      NOT NULL CHECK (discount_minor > 0),
    status         VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at    TIMESTAMPTZ,
    CONSTRAINT uq_coupon_redemptions_order UNIQUE (order_id)
);

CREATE INDEX ix_coupon_redemptions_coupon_user ON coupon_redemptions (coupon_id, user_id) WHERE status = 'ACTIVE';

-- The code a customer applied to their cart (re-validated on every view and at checkout).
ALTER TABLE carts ADD COLUMN coupon_code VARCHAR(32);
-- The code used for an order (snapshot).
ALTER TABLE orders ADD COLUMN coupon_code VARCHAR(32);
