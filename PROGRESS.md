# CommerceX — Build Progress Tracker

> Single source of truth for **where the build is right now**.
> Update this file at the end of every working session / completed task.
>
> Specs: [PRD.md](PRD.md) (what to build) · [TRD.md](TRD.md) (how to build it)

**Legend:** `[ ]` todo · `[~]` in progress · `[x]` done · `[-]` skipped/deferred (with reason)

---

## Current Status

| Field | Value |
|---|---|
| **Current phase** | Phase 10 — Premium Next.js Storefront (next) |
| **Current task** | Phase 9 complete — `./mvnw verify` green (63 unit + 153 integration tests); outage behaviour verified on local stack |
| **Last updated** | 2026-09-28 |
| **Repo state** | Git `main`, Phase 8 = `4842523`. Phase 9 (outbox, Kafka, notifications, Redis, V12) not yet committed |
| **Blockers** | None. Note: app startup varied 17–70 s on dev machine — revisit in Phase 13 performance review |

### Next up
1. Commit Phase 9 (awaiting user go-ahead)
2. Phase 10: scaffold `frontend/` (Next.js App Router + TypeScript + Tailwind + Framer Motion + Lucide), design system, API client (same-site `/api` rewrite, refresh-cookie flow, cart token)
3. Phase 10: home, listing/filters, product detail, cart drawer, checkout, account pages

---

## Phase Overview

| # | Phase | Status |
|---|---|---|
| 0 | Product + Architecture | `[x]` |
| 1 | Spring Boot Foundation | `[x]` |
| 2 | Authentication + Users | `[x]` |
| 3 | Product Catalog | `[x]` |
| 4 | Inventory | `[x]` |
| 5 | Cart + Wishlist | `[x]` |
| 6 | Checkout + Orders | `[x]` |
| 7 | Payments | `[x]` |
| 8 | Promotions + Reviews | `[x]` |
| 9 | Kafka + Outbox + Notifications | `[x]` |
| 10 | Premium Next.js Storefront | `[ ]` |
| 11 | Admin Dashboard | `[ ]` |
| 12 | Testing + Observability + CI/CD | `[ ]` |
| 13 | Production Hardening | `[ ]` |

> Testing is **not** deferred to Phase 12 — every phase ships with its own unit/integration tests (TRD §22 Definition of Done). Phase 12 adds E2E, observability dashboards, and the pipeline.

---

## Phase 0 — Product + Architecture

- [x] Write PRD ([PRD.md](PRD.md))
- [x] Write TRD ([TRD.md](TRD.md))
- [x] Create progress tracker (this file)
- [x] `git init`, `.gitignore`
- [x] Initial commit (`a1a4309`)
- [x] Repository layout (`backend/`, `frontend/`, `infra/`, `docs/`)
- [x] [`docs/architecture.md`](docs/architecture.md) — module map, module boundaries, layering rules
- [x] [`docs/erd.md`](docs/erd.md) — PostgreSQL ERD (users, products, variants, inventory, carts, orders, payments, coupons, reviews, outbox)
- [x] [`docs/domain-model.md`](docs/domain-model.md) — aggregates, order state machine, inventory reservation flow
- [x] [`docs/api.md`](docs/api.md) — REST endpoint outline per module (pagination, error format, idempotency headers)
- [x] [`docs/pages.md`](docs/pages.md) — storefront + admin page map
- [x] [`docs/infra.md`](docs/infra.md) — Docker Compose architecture (app, postgres, redis, kafka)
- [x] [`CLAUDE.md`](CLAUDE.md) — working rules for Claude derived from TRD §19–22

## Phase 1 — Spring Boot Foundation

- [x] Spring Boot 3.5.16 / Java 21 Maven project in `backend/` (with `./mvnw`)
- [x] Package structure: `com.commercex.common` (open Modulith module). Business module packages are created in their own phase
- [x] `infra/docker-compose.yml` with PostgreSQL 17 on port 5442 (Redis/Kafka added when needed)
- [x] Flyway wired, `V1__baseline.sql` (citext, `idempotency_keys`, `audit_log`)
- [x] Global error handling (RFC 7807) with `code`, `correlationId`, field `errors[]`; no internals leaked on 500
- [x] Correlation ID filter (validated against log injection) + logstash JSON logs in `prod` profile
- [x] Actuator: health (+ liveness/readiness), info (build), metrics, prometheus
- [x] OpenAPI `/v3/api-docs` + Swagger UI `/swagger-ui.html` (disabled in `prod`)
- [x] Testcontainers: `AbstractIntegrationTest`, `*IT` via failsafe; `TestCommerceXApplication` for `spring-boot:test-run`
- [x] Module boundary test (Spring Modulith `verify()`)
- [x] `Money` value object (minor units, half-up percentage, overflow-safe) + `PageResponse`

## Phase 2 — Authentication + Users

- [x] `V2__create_users.sql`: users (citext email), user_roles, addresses (one-default partial unique index), refresh_tokens, password_reset_tokens
- [x] Register / login / refresh / logout (`/api/v1/auth/*`)
- [x] Password hashing (BCrypt via `DelegatingPasswordEncoder`, auto-upgrade on login); 10 chars min, 72 bytes max (no silent truncation)
- [x] JWT (RS256) issuance + validation (issuer checked); rotating hashed refresh tokens in httpOnly cookie with reuse detection → family revocation
- [x] RBAC: CUSTOMER, ADMIN, CATALOG_MANAGER, OPERATIONS (`roles` claim → `ROLE_*`, deny-by-default, `@PreAuthorize`); admin customer search + lock/roles; first admin bootstrapped from config
- [x] Customer profile (`/me`), change password (revokes sessions), address book (owner-scoped, max 20, default handling)
- [x] Password reset flow: hashed single-use 30-min token, no account enumeration, email via `notification` module (after-commit async listener → SMTP/Mailpit)
- [x] Rate limiting (Bucket4j + Caffeine, in-memory) on login (IP + email), register (IP), reset (IP + email); 429 + `Retry-After`
- [x] Audit log (own transaction, so failed logins persist): registered, login ok/failed/blocked, logout, refresh reuse, password changed/reset, user updated
- [x] Tests: 4 new unit test classes + `AuthFlowIT`, `PasswordResetIT`, `AddressIT`, `AdminCustomerIT` (29 ITs); smoke-tested against local stack

## Phase 3 — Product Catalog

- [x] Categories (`category` module: tree, max depth 4, cycle prevention, active-branch public tree), brands, tags
- [x] Products: name, slug (auto/explicit), descriptions, currency, DRAFT/PUBLISHED/ARCHIVED lifecycle, optimistic `version` on admin edits
- [x] Variants: global unique SKU, JSONB option values (≤3 options), price/compare-at, matched by SKU on update, deactivated not deleted
- [x] Product images (ordered URLs + alt text; upload/storage deferred)
- [x] Public listing: FTS search, filter (category subtree, brand, price, rating), sort (relevance/newest/price/rating), pagination ≤100, Cache-Control. **Availability filter → Phase 4; popularity sort → after orders exist**
- [x] Admin/catalog-manager CRUD for categories, brands, products (+ publish/unpublish/archive), audited
- [x] Search abstraction: `ProductSearch` port, `PostgresProductSearch` (generated weighted tsvector + GIN, `websearch_to_tsquery`, bound params, whitelisted ORDER BY)
- [x] Tests: `ProductTest`, `SlugsTest`; `CategoryIT`, `CatalogAdminIT`, `CatalogSearchIT` (21 ITs); local smoke test

## Phase 4 — Inventory

- [x] Per-variant stock: on_hand / reserved / sold (available = on_hand − reserved), DB CHECK constraints
- [x] Admin adjustments (RESTOCK/RETURN/ADJUSTMENT, never below reserved) + append-only movement ledger
- [x] `InventoryApi`: reserve (all-or-nothing, sorted, idempotent per order) / commit / release — `MANDATORY` transaction
- [x] Concurrency control: atomic conditional `UPDATE … RETURNING` + `CHECK` constraints (D-02)
- [x] Low-stock threshold per variant, admin low-stock report, `LowStockReached` event (consumer in Phase 9)
- [x] **Concurrency tests: 50 buyers vs. stock 1 → exactly 1; 50 vs. 10 → exactly 10; opposing multi-line orders → no deadlock; CHECK blocks bypass. Mutation-tested.**
- [x] Catalog integration: `in_stock` flags via `StockStatusChanged`, `inStock` filter, per-variant availability on product detail

## Phase 5 — Cart + Wishlist

- [x] Cart (guest via hashed `X-Cart-Token` + authenticated; explicit idempotent merge after login; guest TTL 30 d + hourly cleanup; guest-creation rate limit)
- [x] Add / update / remove / clear; live catalog pricing, per-line issues (UNAVAILABLE / OUT_OF_STOCK / INSUFFICIENT_STOCK), `checkoutReady`; atomic upserts (concurrent tabs tested)
- [x] Wishlist add / remove (idempotent) / view (product cards) / atomic move-to-cart
- [x] Tests: `CartIT` (9), `WishlistIT` (5); shared `ShopFixtures`
- [x] `CatalogApi` (variant snapshots, product cards) as the catalog's public read API

## Phase 6 — Checkout + Orders

- [x] Checkout API: shipping methods, quote, place order (address + shipping + expectedTotal). Payment step → Phase 7
- [x] Server-side re-pricing, shipping rules (free-over threshold), tax placeholder, `expectedTotal` guard (409 PRICE_CHANGED)
- [x] Order creation idempotent via reusable `common.idempotency.Idempotency` (claim → business tx + completion atomically → replay/409/422; released on failure; stale takeover)
- [x] Order state machine (single transition table, exhaustively tested) + append-only history; staff fulfilment transitions; customer cancel
- [x] Stock: reserve on place, commit on PAID, release on cancel; expiry job (`FOR UPDATE SKIP LOCKED`) cancels overdue unpaid orders
- [x] Tests: `OrderStatusTest`; `CheckoutIT` (8), `IdempotentCheckoutIT` (6, incl. 8 concurrent duplicates → 1 order), `OrderLifecycleIT` (9, incl. expiry-vs-late-payment race); repeated 3× for flakiness

## Phase 7 — Payments

- [x] `PaymentProvider` port + deterministic `MockPaymentProvider` (tokens drive outcomes incl. 3DS, timeout-after-charge, outage); provider registry
- [x] Payments: record → provider call outside tx → apply under row lock; one active payment per order (partial unique index); per-(order,key) idempotency + provider idempotency key
- [x] Idempotency on payment + refund (same key → same record, never a second charge/refund)
- [x] Webhooks: HMAC-SHA256 with signed timestamp (replay window 5 min), constant-time compare, dedup table, amount/currency verification
- [x] Failed payment (retry with new key), admin refunds (full/partial, bounded, lifecycle-checked), automatic refund of late payments on cancelled orders
- [x] Reconciliation job for unsettled payments and pending refunds ("succeeded but timed out" tested)
- [x] Tests: `WebhookSignatureTest`, `PaymentTest`; `PaymentFlowIT` (9), `PaymentWebhookIT` (7), `RefundIT` (5)

## Phase 8 — Promotions + Reviews

- [x] Coupons: %/fixed (cap), min amount, date window, usage limits (atomic, concurrency + mutation tested), per-customer limits, product/category(+sub) restrictions; admin CRUD
- [x] Coupon in cart (apply/remove, live re-validation with reason), quote and checkout (redeemed in the order transaction; released on cancel)
- [x] Reviews: delivered purchasers only, one per product, edit → re-moderation, PENDING/APPROVED/REJECTED, public list + summary
- [x] Product rating + review count aggregation (advisory lock per product; concurrent approvals tested)
- [x] Tests: `CouponRulesTest`; `PromotionIT` (7), `ReviewIT` (5)

## Phase 9 — Kafka + Outbox + Notifications

- [x] Outbox (`common.messaging.Outbox`, written in the business tx) + `OutboxRelay` (SKIP LOCKED, backoff, stop-on-failure, pending gauge, retention cleanup)
- [x] Kafka 3.9.1 KRaft in docker-compose + Testcontainers; topics created by the app; producer idempotence + fail-fast timeouts
- [x] Integration events: OrderPlaced, OrderStatusChanged (covers paid/failed/shipped/delivered/cancelled/refunded), UserRegistered, LowStockReached
- [x] Notification consumer → emails for order lifecycle, welcome, low-stock (ops); `EmailSender` channel abstraction
- [x] Idempotent consumers (`processed_events`), retries + dead-letter topics
- [x] Redis: distributed rate limiter (Lua fixed window, in-memory fallback when Redis is down), category-tree + brand caches with eviction. Idempotency stays in PostgreSQL (D-30)
- [x] Tests: `RedisRateLimiterTest`; `OutboxIT`, `NotificationIT` (incl. duplicate delivery), `RedisIT`; manual Kafka/Redis outage checks

## Phase 10 — Premium Next.js Storefront

- [ ] Next.js + TypeScript + Tailwind + Framer Motion + Lucide in `frontend/`
- [ ] Design system (typography, colors, spacing tokens)
- [ ] Homepage: hero, featured, new arrivals, trending, categories, promo, brand story, testimonials, newsletter, footer
- [ ] Product listing with filters
- [ ] Product detail with variant selection + shared-layout transition
- [ ] Cart drawer, wishlist
- [ ] Checkout steps
- [ ] Account: orders, tracking, addresses, reviews
- [ ] Skeletons, empty/error states, toasts
- [ ] Accessibility + SEO + mobile-first pass

## Phase 11 — Admin Dashboard

- [ ] Overview KPIs: revenue, orders, customers, products, low stock, pending orders, failed payments, recent activity
- [ ] Management: products, categories, brands, inventory, orders, customers, coupons, reviews
- [ ] Role-based access per screen

## Phase 12 — Testing + Observability + CI/CD

- [ ] E2E: register → login → browse → product → cart → checkout → payment → order → admin processing
- [ ] Prometheus + Grafana dashboards (latency, errors, DB, JVM, Redis, Kafka lag, payment/order failures)
- [ ] CI: compile → static analysis → unit → integration → Docker build → security scan → publish → deploy

## Phase 13 — Production Hardening

- [ ] Security review (headers, secrets management, input validation audit, logging of sensitive data)
- [ ] Performance review (query plans, N+1, indexes, caching)
- [ ] Failure-mode review (TRD §20 checklist for every critical flow)
- [ ] Deployment documentation

---

## Decisions Log

| ID | Decision | Status | Choice / Notes |
|---|---|---|---|
| D-01 | Architecture style | ✅ Decided | Modular monolith, single Spring Boot app (TRD §3) |
| D-02 | Inventory concurrency strategy | ✅ Decided | Atomic conditional `UPDATE … WHERE on_hand - reserved >= :qty` + `CHECK (reserved <= on_hand)`; reserve in ascending variant ID order. Concurrency test required |
| D-03 | Build tool | ✅ Decided | Maven (with wrapper) |
| D-04 | Auth approach | ✅ Decided | Self-issued JWT (RS256) via Spring Security resource server; opaque rotating refresh tokens. Keycloak only if needed |
| D-05 | Repo layout | ✅ Decided | Monorepo — `backend/`, `frontend/`, `infra/`, `docs/` |
| D-06 | Base package | ✅ Decided | `com.commercex` |
| D-07 | Module boundary enforcement | ✅ Decided | Spring Modulith verification test |
| D-08 | Primary keys | ✅ Decided | `BIGINT` from per-table sequences (allocationSize 50); public URLs use slugs / order numbers |
| D-09 | Money | ✅ Decided | `BIGINT` minor units + `CHAR(3)` currency, `Money` value object |
| D-10 | Error format | ✅ Decided | RFC 7807 `ProblemDetail` |
| D-11 | Frontend structure | ✅ Decided | One Next.js app; storefront + `/admin` route groups |
| D-12 | Local ports | ✅ Decided | Shifted to avoid other local stacks: Postgres 5442, Redis 6389, Mailpit 1026/8026, Prometheus 9091, Grafana 3002 |
| D-13 | Test split | ✅ Decided | `*Test` = unit/slice (surefire), `*IT` = Testcontainers integration (failsafe); `./mvnw verify` runs both |
| D-14 | Threads | ✅ Decided | Virtual threads enabled (`spring.threads.virtual.enabled`) |
| D-15 | Cross-module events (pre-Kafka) | ✅ Decided | In-process `@Async @TransactionalEventListener(AFTER_COMMIT)`; best effort until the outbox (Phase 9). Events carrying secrets (reset link) stay in-process only |
| D-16 | Rate limiter | ✅ Decided | `RateLimiter` interface; in-memory Bucket4j now, Redis-backed when multi-instance (Phase 9) |
| D-17 | Actuator in prod | ✅ Decided | Separate internal port 8081; health/info/prometheus public on it, other endpoints ADMIN |
| D-18 | Refresh-token CSRF | ✅ Decided | Cookie is SameSite=Strict + path-scoped; frontend will call the API same-site (Next.js rewrites `/api`) |
| D-19 | Module public API | ✅ Decided | Types in a module's base package are its API (e.g. `CategoryApi`); sub-packages internal, enforced by Modulith |
| D-20 | Variant options storage | ✅ Decided | JSONB `option_values` on variants + ordered option names, instead of option/value/join tables |
| D-21 | Variant lifecycle | ✅ Decided | Variants never deleted (deactivated); identity = SKU, globally unique |
| D-22 | Admin edit concurrency | ✅ Decided | Client sends `version` on PUT; mismatch → 409 (no lost updates) |
| D-23 | Store currency | ✅ Decided | Single currency from `commercex.catalog.currency` (USD) |
| D-24 | Inventory ↔ catalog dependency | ✅ Decided | Catalog depends on `InventoryApi`; inventory never depends on catalog. Stock flags flow via synchronous in-transaction `StockStatusChanged` events (zero-crossings only) |
| D-25 | Reservation expiry owner | ✅ Decided | Order module (Phase 6) cancels expired orders and releases; inventory never auto-releases |
| D-26 | Public stock exposure | ✅ Decided | Customers see IN_STOCK / LOW_STOCK / OUT_OF_STOCK, not exact counts |
| D-27 | Guest cart identity | ✅ Decided | Opaque `X-Cart-Token` (header, not cookie), stored as SHA-256; bearer user cart takes precedence; explicit `POST /cart/merge` |
| D-28 | Cart pricing | ✅ Decided | Cart stores no prices; every view is priced from `CatalogApi`; problem lines flagged and excluded, never silently dropped |
| D-29 | Cart stock | ✅ Decided | Stock checked on add/update but reserved only at checkout (no cart holds) |
| D-30 | Idempotency store | ✅ Decided | PostgreSQL `idempotency_keys`; key completion commits atomically with the business data; failed attempts release the key; stale IN_PROGRESS (> 2 min) may be taken over; 24 h retention |
| D-31 | Price-change guard | ✅ Decided | Client must send `expectedTotal`; mismatch → 409 PRICE_CHANGED |
| D-32 | Order row locking | ✅ Decided | All status changes load the order `FOR UPDATE`; expiry job uses `SKIP LOCKED` |
| D-33 | Payment window | ✅ Decided | 30 min (`commercex.orders.payment-window`); PAYMENT_FAILED keeps the reservation until then |
| D-34 | Tax | ⏳ Open | Placeholder 0 (prices tax-inclusive); decide on a tax provider before launch |
| D-35 | Payment call boundaries | ✅ Decided | Never call the provider inside a DB transaction: record → call → apply; unknown outcomes stay PENDING for webhook/reconciliation |
| D-36 | Webhook matching | ✅ Decided | Our payment/refund IDs are sent as provider metadata and echoed in webhooks (works before our own call returns) |
| D-37 | Late payment | ✅ Decided | `OrderApi.markPaid` returns false for non-payable orders → automatic full refund; order stays CANCELLED |
| D-38 | Refund restocking | ✅ Decided | Not automatic; staff inspect returns and use inventory RETURN adjustments |
| D-39 | Real provider | ⏳ Open | Only the mock adapter exists; pick provider (e.g. Stripe) and implement its adapter + webhook translation before launch |
| D-40 | Coupon usage concurrency | ✅ Decided | Atomic conditional `used_count` increment (also serializes per-customer checks) inside the order transaction; CHECK constraint as backstop |
| D-41 | Coupons & cancellations | ✅ Decided | Cancelled orders release their redemption; refunds do not |
| D-42 | Review eligibility | ✅ Decided | Requires a DELIVERED order containing the product; moderation before publishing |
| D-43 | Rating updates | ✅ Decided | Review module calls `CatalogApi.updateRating` (one-way dependency) after recomputing under an advisory lock |
| D-44 | Event publication | ✅ Decided | Own transactional outbox table + scheduled relay (not Spring Modulith externalization): explicit, observable, retry/backoff under our control |
| D-45 | Event contract | ✅ Decided | Envelope `{eventId, type, occurredAt, data}`, versioned payload records (`…V1`), additive changes only; secrets never externalized |
| D-46 | Delivery semantics | ✅ Decided | At-least-once relay + consumer dedup by eventId; no strict cross-event ordering guarantee |
| D-47 | Redis failure mode | ✅ Decided | Fail open to per-instance limits / DB reads; circuit breaker to cut timeout latency in Phase 13 |

---

## Session Log

| Date | Phase | Work done |
|---|---|---|
| 2026-09-28 | 0 | Read PRD + TRD. Created PROGRESS.md tracker. |
| 2026-09-28 | 0 | Decisions D-02..D-11 made. git init + .gitignore. Wrote docs/ (architecture, erd, domain-model, api, pages, infra) and CLAUDE.md. Phase 0 done. |
| 2026-09-28 | 1 | Scaffolded backend (Boot 3.5.16, Modulith 1.4.13, springdoc 2.8.17), common module, V1 migration, compose Postgres. Fixed ProblemDetail enrichment for framework exceptions (405/415). `./mvnw verify` green; smoke-tested running app. |
| 2026-09-28 | 2 | Committed Phase 1 (`7a56fde`). Built auth module (JWT RS256, rotating refresh cookies, RBAC, profile, addresses, password reset, admin customers, bootstrap admin), common security/rate-limit/audit, notification module (email), Mailpit. Fixed CHAR/CITEXT schema-validation mapping. `./mvnw verify` green (31 unit + 35 IT); local smoke test incl. Mailpit delivery. |
| 2026-09-28 | 3 | Committed Phase 2 (`16d8b15`). Built category + catalog modules (V3–V4), FTS search, admin CRUD with optimistic locking, public catalog API. `./mvnw verify` green (45 unit + 56 IT); local smoke test. |
| 2026-09-28 | 4 | Committed Phase 3 (`c0b89ef`). Built inventory module (V5–V6): atomic reservations, ledger, admin adjustments/thresholds, catalog stock flags + availability. Concurrency suite passes and was mutation-tested. `./mvnw verify` green (46 unit + 75 IT). |
| 2026-09-28 | 5 | Committed Phase 4 (`aae8e82`). Built cart + wishlist modules (V7), `CatalogApi`, scheduling. Fixed untyped timestamp param in CASE. `./mvnw verify` green (46 unit + 89 IT). |
| 2026-09-28 | 6 | Committed Phase 5 (`80a8d18`). Built idempotency infra, auth `AccountApi`, order module (V8, state machine, expiry job) and checkout module. Fixed checkout reporting "empty cart" when all items were unavailable. `./mvnw verify` green (50 unit + 112 IT); concurrency ITs repeated 3×. |
| 2026-09-28 | 7 | Committed Phase 6 (`b9d40ef`). Built payment module (V9): provider port + mock, payments/refunds outside DB tx, signed deduplicated webhooks, reconciliation, auto-refund of late payments. `OrderApi.markPaid` now returns boolean. `./mvnw verify` green (57 unit + 133 IT). |
| 2026-09-28 | 8 | Committed Phase 7 (`21e3222`). Built promotion module (V10: coupons, rules, atomic redemption, cart/checkout integration, release on cancel) and review module (V11: eligibility, moderation, rating aggregation). Removed a catalog↔review module cycle. Coupon concurrency test mutation-tested. `./mvnw verify` green (62 unit + 145 IT). |
| 2026-09-28 | 9 | Committed Phase 8 (`4842523`). Built outbox + relay (V12), Kafka topics/error handling, integration events in order/auth/inventory, notification consumer, Redis rate limiter + caches; compose + Testcontainers for Kafka/Redis. `./mvnw verify` green (63 unit + 153 IT). Verified Kafka-down (events queued then delivered) and Redis-down (fallback) on local stack. |
