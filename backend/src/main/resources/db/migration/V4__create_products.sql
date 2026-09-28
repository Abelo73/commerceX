-- Catalog module: products, variants, options, images, tags, full-text search.

CREATE SEQUENCE products_seq INCREMENT BY 50;

CREATE TABLE products (
    id                BIGINT        PRIMARY KEY,
    slug              VARCHAR(160)  NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    short_description VARCHAR(500),
    description       TEXT,
    brand_id          BIGINT        REFERENCES brands (id),
    category_id       BIGINT        NOT NULL REFERENCES categories (id),
    status            VARCHAR(16)   NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    currency          CHAR(3)       NOT NULL,
    -- Denormalized from active variants (maintained by the application) for fast price filter/sort.
    min_price_minor   BIGINT,
    rating_avg        NUMERIC(3, 2) NOT NULL DEFAULT 0,
    review_count      INT           NOT NULL DEFAULT 0,
    -- Brand name + tags, maintained by the application so they are searchable.
    search_keywords   TEXT,
    search_vector     TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(search_keywords, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(short_description, '')), 'C') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'D')
    ) STORED,
    published_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_published_has_price CHECK (status <> 'PUBLISHED' OR min_price_minor IS NOT NULL)
);

CREATE INDEX ix_products_search ON products USING GIN (search_vector);
CREATE INDEX ix_products_status_category ON products (status, category_id);
CREATE INDEX ix_products_status_brand ON products (status, brand_id);
CREATE INDEX ix_products_status_price ON products (status, min_price_minor);
CREATE INDEX ix_products_status_published ON products (status, published_at DESC);

-- Ordered option names, e.g. [Color, Size].
CREATE TABLE product_options (
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    position   INT         NOT NULL,
    name       VARCHAR(50) NOT NULL,
    PRIMARY KEY (product_id, position)
);

CREATE SEQUENCE product_variants_seq INCREMENT BY 50;

-- Variants are never deleted (carts, inventory and orders reference them); they are deactivated.
CREATE TABLE product_variants (
    id                     BIGINT       PRIMARY KEY,
    product_id             BIGINT       NOT NULL REFERENCES products (id),
    sku                    VARCHAR(64)  NOT NULL,
    option_values          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    price_minor            BIGINT       NOT NULL CHECK (price_minor > 0),
    compare_at_price_minor BIGINT       CHECK (compare_at_price_minor IS NULL OR compare_at_price_minor > price_minor),
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    position               INT          NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_variants_sku UNIQUE (sku),
    CONSTRAINT uq_product_variants_combination UNIQUE (product_id, option_values)
);

CREATE INDEX ix_product_variants_product ON product_variants (product_id);
CREATE INDEX ix_product_variants_options ON product_variants USING GIN (option_values);

CREATE TABLE product_images (
    product_id BIGINT        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    position   INT           NOT NULL,
    url        VARCHAR(2048) NOT NULL,
    alt_text   VARCHAR(300),
    PRIMARY KEY (product_id, position)
);

CREATE TABLE product_tags (
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    tag        VARCHAR(50) NOT NULL,
    PRIMARY KEY (product_id, tag)
);

CREATE INDEX ix_product_tags_tag ON product_tags (tag);
