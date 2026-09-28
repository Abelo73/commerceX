-- Catalog read-model flags maintained from inventory stock-status events (in-stock filter).
ALTER TABLE product_variants ADD COLUMN in_stock BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE products ADD COLUMN in_stock BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX ix_products_status_in_stock ON products (status, in_stock);
