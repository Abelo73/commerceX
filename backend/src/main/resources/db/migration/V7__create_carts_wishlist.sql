-- Cart module: user carts (one per user) and guest carts (identified by a hashed opaque token).
-- Cart lines never store prices: totals are always recomputed from the catalog.

CREATE SEQUENCE carts_seq INCREMENT BY 50;

CREATE TABLE carts (
    id               BIGINT      PRIMARY KEY DEFAULT nextval('carts_seq'),
    user_id          BIGINT      REFERENCES users (id) ON DELETE CASCADE,
    guest_token_hash CHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Guest carts expire after inactivity; user carts never do.
    expires_at       TIMESTAMPTZ,
    CONSTRAINT uq_carts_user UNIQUE (user_id),
    CONSTRAINT uq_carts_guest_token UNIQUE (guest_token_hash),
    CONSTRAINT ck_carts_exactly_one_owner CHECK ((user_id IS NULL) <> (guest_token_hash IS NULL))
);

CREATE INDEX ix_carts_guest_expiry ON carts (expires_at) WHERE user_id IS NULL;

CREATE TABLE cart_items (
    cart_id    BIGINT      NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    variant_id BIGINT      NOT NULL REFERENCES product_variants (id),
    quantity   INT         NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (cart_id, variant_id)
);

-- Wishlist module.
CREATE TABLE wishlist_items (
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL REFERENCES products (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id)
);

CREATE INDEX ix_wishlist_items_user_created ON wishlist_items (user_id, created_at DESC);
