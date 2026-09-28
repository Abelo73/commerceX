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
GET /categories                            🌐  tree
GET /brands                                🌐
GET /products?q=&category=&brand=&minPrice=&maxPrice=&minRating=&inStock=&sort=   🌐
GET /products/{slug}                       🌐  with variants, options, images, availability
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
CRUD   /admin/products, /variants, /images 🛠📦
CRUD   /admin/categories, /brands          🛠📦
GET    /admin/inventory?lowStock=true      🛠🚚
POST   /admin/inventory/{variantId}/adjustments   🛠🚚
GET    /admin/inventory/{variantId}/movements     🛠🚚
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
