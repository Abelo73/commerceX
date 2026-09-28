-- Category module: category tree. Catalog module: brands.

CREATE SEQUENCE categories_seq INCREMENT BY 50;

CREATE TABLE categories (
    id          BIGINT       PRIMARY KEY,
    parent_id   BIGINT       REFERENCES categories (id),
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    position    INT          NOT NULL DEFAULT 0,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX ix_categories_parent ON categories (parent_id);

CREATE SEQUENCE brands_seq INCREMENT BY 50;

CREATE TABLE brands (
    id          BIGINT        PRIMARY KEY,
    name        VARCHAR(120)  NOT NULL,
    slug        VARCHAR(160)  NOT NULL,
    description VARCHAR(2000),
    logo_url    VARCHAR(2048),
    is_active   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_brands_slug UNIQUE (slug)
);
