# API Outline

Base path: `/api/v1`. JSON only. OpenAPI served at `/swagger-ui.html` (`/v3/api-docs`).

## Conventions

| Topic | Rule |
|---|---|
| Auth | `Authorization: Bearer <access JWT>` (RS256, 15 min, `sub` = user ID, `roles` claim). Refresh token only in the httpOnly, Secure, SameSite=Strict `cx_refresh` cookie scoped to `/api/v1/auth`; rotated on every refresh, reuse revokes the whole session family. Deny-by-default: unknown routes return 401 to anonymous callers |
| Errors | RFC 7807 `application/problem+json`: `type`, `title`, `status`, `detail`, `instance`, `code`, `correlationId`, `errors[]` (field validation) |
| Pagination | `?page=0&size=20&sort=price,asc` (size max 100). Response: `{ content, page, size, totalElements, totalPages }` |
| Idempotency | `Idempotency-Key` header **required** on `POST /orders`, `POST /orders/{n}/payments`, `POST /admin/payments/{id}/refunds`. Same key + different body → `422` |
| Correlation | `X-Correlation-Id` accepted/generated, echoed in response and logs |
| Money | `{ "amount": 12000, "currency": "USD" }` (minor units) |
| Rate limits | `429` + `Retry-After` |

Common error codes: `VALIDATION_FAILED` / `INVALID_RESET_TOKEN` (400), `UNAUTHENTICATED` / `INVALID_CREDENTIALS` / `INVALID_REFRESH_TOKEN` (401), `FORBIDDEN` / `ACCOUNT_DISABLED` (403), `EMAIL_ALREADY_REGISTERED` (409), `NOT_FOUND` (404), `CONFLICT` / `INSUFFICIENT_STOCK` / `INVALID_STATE_TRANSITION` / `IDEMPOTENCY_KEY_IN_PROGRESS` (409), `IDEMPOTENCY_KEY_MISMATCH` / `COUPON_INVALID` (422), `RATE_LIMITED` (429).

## Endpoints

Access: 🌐 public · 👤 customer · 🛠 ADMIN · 📦 CATALOG_MANAGER · 🚚 OPERATIONS

### Auth & account
```text
POST   /auth/register                      🌐
POST   /auth/login                         🌐  (rate limited)
POST   /auth/refresh                       🌐  (cookie)
POST   /auth/logout                        🌐  (cookie; idempotent)
POST   /auth/password-reset/request        🌐  (always 202 — no user enumeration)
POST   /auth/password-reset/confirm        🌐
GET    /me                                 👤
PATCH  /me                                 👤
PUT    /me/password                        👤
GET    /me/addresses                       👤
POST   /me/addresses                       👤
PUT    /me/addresses/{id}                  👤
DELETE /me/addresses/{id}                  👤
```

### Catalog (public)
```text
GET /categories                            🌐  active tree (Cache-Control: public, 5 min)
GET /brands                                🌐  active brands
GET /products?q=&category=&brand=&minPrice=&maxPrice=&minRating=&sort=&page=&size=   🌐  (60 s cache)
      q: websearch syntax ("quoted", or, -exclude); category: slug incl. subcategories;
      brand: slug(s); prices in minor units; inStock=true; sort: relevance|newest|price_asc|price_desc|rating;
      size ≤ 100
GET /products/{slug}                       🌐  published only: options, active variants (availability IN_STOCK|LOW_STOCK|OUT_OF_STOCK — exact counts hidden), images, tags
GET /products/{slug}/reviews               🌐  approved only
```

### Cart & wishlist
```text
GET    /cart                               🌐👤  (guest via X-Cart-Token)
POST   /cart/items                         🌐👤  { variantId, quantity }
PATCH  /cart/items/{variantId}             🌐👤
DELETE /cart/items/{variantId}             🌐👤
PUT    /cart/coupon                        🌐👤
DELETE /cart/coupon                        🌐👤
POST   /cart/merge                         👤    guest → user on login
GET    /wishlist                           👤
PUT    /wishlist/{productId}               👤
DELETE /wishlist/{productId}               👤
POST   /wishlist/{productId}/move-to-cart  👤
```

### Checkout & orders
```text
GET  /checkout/shipping-methods            👤
POST /checkout/quote                       👤  server-side totals preview
POST /orders                               👤  Idempotency-Key
GET  /orders                               👤  own orders
GET  /orders/{orderNumber}                 👤  own order + status history
POST /orders/{orderNumber}/cancel          👤  only while unpaid
POST /orders/{orderNumber}/payments        👤  Idempotency-Key
POST /reviews                              👤  verified purchasers only
```

### Payments (provider-facing)
```text
POST /payments/webhooks/{provider}         🌐  signature-verified, deduplicated
```

### Admin (`/admin/...`)
```text
GET    /admin/dashboard                    🛠
GET    /admin/products?status=&q=          🛠📦
GET    /admin/products/{id}                🛠📦
POST   /admin/products                     🛠📦  full document incl. variants → DRAFT
PUT    /admin/products/{id}                🛠📦  full replace; requires `version`; variants matched by SKU, omitted ones deactivated
POST   /admin/products/{id}/publish|unpublish|archive   🛠📦
GET/POST /admin/categories, PUT/DELETE /admin/categories/{id}   🛠📦  PUT requires `version`; max depth 4; no cycles
GET/POST /admin/brands, PUT/DELETE /admin/brands/{id}           🛠📦  PUT requires `version`
GET    /admin/inventory?lowStock=&page=&size=      🛠🚚  lowest availability first
GET    /admin/inventory/{variantId}                🛠🚚
POST   /admin/inventory/{variantId}/adjustments    🛠🚚  { type: RESTOCK|RETURN|ADJUSTMENT, quantity (signed), reason } — never below reserved
PUT    /admin/inventory/{variantId}/threshold      🛠🚚  { lowStockThreshold }
GET    /admin/inventory/{variantId}/movements      🛠🚚  ledger, newest first
GET    /admin/orders?status=               🛠🚚
POST   /admin/orders/{orderNumber}/transitions    🛠🚚  { toStatus, reason }
POST   /admin/payments/{id}/refunds        🛠   Idempotency-Key
GET    /admin/customers?q=                 🛠   email search, paginated
PATCH  /admin/customers/{id}               🛠   { status?, roles? } — cannot lock self / drop own ADMIN
CRUD   /admin/coupons                      🛠
GET    /admin/reviews?status=PENDING       🛠
POST   /admin/reviews/{id}/approve|reject  🛠
GET    /admin/audit-log                    🛠
```
