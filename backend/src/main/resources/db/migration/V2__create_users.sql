-- Auth module: users, roles, addresses, refresh tokens, password reset tokens.
-- IDs are assigned by Hibernate from the sequences (pooled, increment 50); no column DEFAULTs,
-- so that SQL-default and Hibernate-allocated IDs can never collide.

CREATE SEQUENCE users_seq INCREMENT BY 50;

CREATE TABLE users (
    id             BIGINT       PRIMARY KEY,
    email          CITEXT       NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    status         VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(32) NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN', 'CATALOG_MANAGER', 'OPERATIONS')),
    PRIMARY KEY (user_id, role)
);

CREATE SEQUENCE addresses_seq INCREMENT BY 50;

CREATE TABLE addresses (
    id          BIGINT       PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label       VARCHAR(50),
    full_name   VARCHAR(200) NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200),
    city        VARCHAR(100) NOT NULL,
    region      VARCHAR(100),
    postal_code VARCHAR(20)  NOT NULL,
    country     CHAR(2)      NOT NULL,
    phone       VARCHAR(32),
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX ix_addresses_user ON addresses (user_id);
-- At most one default address per user, enforced by the database.
CREATE UNIQUE INDEX uq_addresses_user_default ON addresses (user_id) WHERE is_default;

CREATE SEQUENCE refresh_tokens_seq INCREMENT BY 50;

CREATE TABLE refresh_tokens (
    id             BIGINT      PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     CHAR(64)    NOT NULL,
    family_id      UUID        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id BIGINT,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);

CREATE SEQUENCE password_reset_tokens_seq INCREMENT BY 50;

CREATE TABLE password_reset_tokens (
    id         BIGINT      PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
