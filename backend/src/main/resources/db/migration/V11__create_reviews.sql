-- Review module: verified-purchase reviews with moderation.

CREATE SEQUENCE reviews_seq INCREMENT BY 50;

CREATE TABLE reviews (
    id                BIGINT       PRIMARY KEY,
    product_id        BIGINT       NOT NULL REFERENCES products (id),
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    order_id          BIGINT       NOT NULL REFERENCES orders (id),
    author_name       VARCHAR(120) NOT NULL,
    rating            SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title             VARCHAR(150) NOT NULL,
    comment           VARCHAR(5000),
    verified_purchase BOOLEAN      NOT NULL,
    status            VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    rejection_reason  VARCHAR(500),
    moderated_by      BIGINT,
    moderated_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_reviews_product_user UNIQUE (product_id, user_id)
);

CREATE INDEX ix_reviews_product_approved ON reviews (product_id, created_at DESC) WHERE status = 'APPROVED';
CREATE INDEX ix_reviews_status_created ON reviews (status, created_at);
