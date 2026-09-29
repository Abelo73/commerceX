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
GET /products/{slug}/reviews?sort=NEWEST|HIGHEST|LOWEST   🌐  { summary {average, count, distribution}, reviews (approved, author "Ada L.") }
```

### Cart & wishlist
```text
GET    /cart                               🌐👤  bearer → user cart; else X-Cart-Token → guest cart; else empty
POST   /cart/items                         🌐👤  { variantId, quantity } — first guest add returns X-Cart-Token (header + body `cartToken`)
PATCH  /cart/items/{variantId}             🌐👤  { quantity }
DELETE /cart/items/{variantId}             🌐👤
DELETE /cart                               🌐👤  clear
POST   /cart/merge                         👤    X-Cart-Token → folded into user cart (summed, capped at 99), idempotent
PUT    /cart/coupon                        🌐👤  { code } → 422 COUPON_INVALID {details.reason} if it does not qualify
DELETE /cart/coupon                        🌐👤
      Cart response adds `coupon {code, valid, discount, reason, message}`, `discount`, `total` (before shipping)
      Cart response: lines priced live from the catalog; line `issue` = UNAVAILABLE | OUT_OF_STOCK |
      INSUFFICIENT_STOCK (+ availableQuantity), excluded from subtotal; `checkoutReady`.
      Limits: 99 per line, 50 lines; stock checked on add/update (not reserved until checkout).
GET    /wishlist                           👤  newest first; unpublished products stay with available=false
PUT    /wishlist/{productId}               👤  idempotent; max 200
DELETE /wishlist/{productId}               👤  idempotent
POST   /wishlist/{productId}/move-to-cart  👤  { variantId, quantity } — atomic: wishlist unchanged if the cart rejects
```

### Checkout & orders
```text
GET  /checkout/shipping-methods            👤  STANDARD (5.99, free ≥ 100.00), EXPRESS (14.99)
POST /checkout/quote                       👤  { shippingMethod? } → lines, subtotal, shipping, tax, total, options, ready, problems
POST /orders                               👤  { addressId, shippingMethod, expectedTotal } + Idempotency-Key (8–255 chars)
                                                → 201 OrderDetails; replay → same body + `Idempotent-Replayed: true`
                                                409 PRICE_CHANGED | CHECKOUT_NOT_READY | INSUFFICIENT_STOCK | IDEMPOTENCY_KEY_IN_PROGRESS
                                                422 IDEMPOTENCY_KEY_MISMATCH
GET  /orders                               👤  own orders (summary, newest first)
GET  /orders/{orderNumber}                 👤  own order: items, totals, address, status history
POST /orders/{orderNumber}/cancel          👤  only while unpaid; releases stock
POST /orders/{orderNumber}/payments        👤  { paymentToken } + Idempotency-Key → 201 new / 200 same key; status PENDING | REQUIRES_ACTION (nextActionUrl) | SUCCEEDED | FAILED
                                                409 ORDER_NOT_PAYABLE | PAYMENT_IN_PROGRESS
GET  /orders/{orderNumber}/payments        👤  own payments
POST /reviews                              👤  { productId, rating 1–5, title, comment } — delivered purchasers only (403 otherwise); → PENDING
PUT  /reviews/{id}, DELETE /reviews/{id}   👤  own reviews; edits return to moderation
GET  /me/reviews                           👤
```

### Payments (provider-facing)
```text
POST /payments/webhooks/{provider}         🌐  header X-CommerceX-Signature: t=<unix>,v1=<hex HMAC-SHA256(secret, t.body)>;
                                                401 INVALID_SIGNATURE; duplicates → 200 {outcome: DUPLICATE}
```

### Admin (`/admin/...`)
```text
GET    /admin/dashboard?days=1..90     🛠🚚  KPIs for the last N UTC days incl. today, daily revenue series, recent staff activity
GET    /admin/stock?lowStockOnly=&page=&size=  🛠🚚  inventory rows with product name, SKU and variant label
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
GET    /admin/orders?status=&q=            🛠🚚  q = exact order number or customer email
GET    /admin/orders/{orderNumber}         🛠🚚
POST   /admin/orders/{orderNumber}/transitions    🛠🚚  { toStatus: PROCESSING|PACKED|SHIPPED|DELIVERED|CANCELLED, reason } — state machine enforced
GET    /admin/orders/{orderNumber}/payments    🛠🚚  payments with provider refs + refunds
POST   /admin/payments/{id}/refunds        🛠   { amount?, reason } + Idempotency-Key; 409 REFUND_NOT_ALLOWED (e.g. SHIPPED)
GET    /admin/customers?q=                 🛠   email search, paginated
PATCH  /admin/customers/{id}               🛠   { status?, roles? } — cannot lock self / drop own ADMIN
GET/POST /admin/coupons, GET/PUT /admin/coupons/{id}    🛠  PERCENTAGE value in basis points; restrictions productIds/categoryIds; PUT requires `version`; deactivate instead of delete
GET    /admin/reviews?status=PENDING       🛠   oldest first
POST   /admin/reviews/{id}/approve         🛠
POST   /admin/reviews/{id}/reject          🛠   { reason }
GET    /admin/audit-log                    🛠
```
