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
| **Current phase** | Phase 3 — Product Catalog (next) |
| **Current task** | Phase 2 complete — `./mvnw verify` green (31 unit + 35 integration tests) |
| **Last updated** | 2026-09-28 |
| **Repo state** | Git `main`, Phase 1 = `7a56fde`. Phase 2 (auth + notification modules, V2) not yet committed |
| **Blockers** | None. Note: app startup varied 17–70 s on dev machine — revisit in Phase 13 performance review |

### Next up
1. Commit Phase 2 (awaiting user go-ahead)
2. Phase 3: `V3__create_categories_brands.sql`, `V4__create_products.sql` (products, variants, options, images, tags, FTS)
3. Phase 3: public catalog API (search/filter/sort/paginate) + admin/catalog-manager CRUD

---

## Phase Overview

| # | Phase | Status |
|---|---|---|
| 0 | Product + Architecture | `[x]` |
| 1 | Spring Boot Foundation | `[x]` |
| 2 | Authentication + Users | `[x]` |
| 3 | Product Catalog | `[ ]` |
| 4 | Inventory | `[ ]` |
| 5 | Cart + Wishlist | `[ ]` |
| 6 | Checkout + Orders | `[ ]` |
| 7 | Payments | `[ ]` |
| 8 | Promotions + Reviews | `[ ]` |
| 9 | Kafka + Outbox + Notifications | `[ ]` |
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

- [ ] Categories (hierarchical), brands, tags
- [ ] Products: name, slug, SKU, descriptions, price, compare-at price, currency, published state
- [ ] Variants + attributes (color, size, …)
- [ ] Product images
- [ ] Public listing: search, filter (category, brand, price, rating, availability), sort, pagination
- [ ] Admin/catalog-manager CRUD
- [ ] Search abstraction (PostgreSQL FTS now, OpenSearch-ready)
- [ ] Tests

## Phase 4 — Inventory

- [ ] Per-variant stock: available / reserved / sold
- [ ] Stock adjustments + inventory history
- [ ] Reservation + release
- [ ] Concurrency control: atomic conditional `UPDATE` + `CHECK` constraints (D-02)
- [ ] Low-stock threshold + query
- [ ] **Concurrency test: N buyers vs. stock = 1 → exactly one succeeds**

## Phase 5 — Cart + Wishlist

- [ ] Cart (guest + authenticated, merge on login)
- [ ] Add / remove / change quantity, server-side price validation
- [ ] Wishlist add / remove / view / move to cart
- [ ] Tests

## Phase 6 — Checkout + Orders

- [ ] Checkout flow: customer → address → shipping → payment → review → confirmation
- [ ] Server-side price recalculation + shipping calculation
- [ ] Order creation (idempotent, `Idempotency-Key`)
- [ ] Order state machine + status history (auditable)
- [ ] Inventory reservation on order, release on cancel/failure/timeout
- [ ] Tests (state machine unit tests, checkout integration tests)

## Phase 7 — Payments

- [ ] `PaymentProvider` interface + `MockPaymentProvider`
- [ ] Payment intent, provider reference, status
- [ ] Idempotency on payment + refund
- [ ] Webhook endpoint with signature check + deduplication
- [ ] Failed payment + refund flows
- [ ] Reconciliation for "payment succeeded but request timed out"
- [ ] Tests

## Phase 8 — Promotions + Reviews

- [ ] Coupons: %/fixed, min amount, date window, usage limits, per-customer limits, product/category restrictions
- [ ] Coupon application in cart + checkout
- [ ] Reviews: verified purchase only, PENDING/APPROVED/REJECTED moderation
- [ ] Product rating + review count aggregation
- [ ] Tests (pricing/promotion unit tests)

## Phase 9 — Kafka + Outbox + Notifications

- [ ] Outbox table + publisher
- [ ] Kafka in docker-compose
- [ ] Domain events: OrderPlaced, PaymentSucceeded, PaymentFailed, OrderShipped, OrderDelivered
- [ ] Notification module with channel abstraction (email first; SMS/push pluggable)
- [ ] Idempotent consumers
- [ ] Redis: catalog caching, rate limiting, idempotency support
- [ ] Tests (Kafka Testcontainers)

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

---

## Session Log

| Date | Phase | Work done |
|---|---|---|
| 2026-09-28 | 0 | Read PRD + TRD. Created PROGRESS.md tracker. |
| 2026-09-28 | 0 | Decisions D-02..D-11 made. git init + .gitignore. Wrote docs/ (architecture, erd, domain-model, api, pages, infra) and CLAUDE.md. Phase 0 done. |
| 2026-09-28 | 1 | Scaffolded backend (Boot 3.5.16, Modulith 1.4.13, springdoc 2.8.17), common module, V1 migration, compose Postgres. Fixed ProblemDetail enrichment for framework exceptions (405/415). `./mvnw verify` green; smoke-tested running app. |
| 2026-09-28 | 2 | Committed Phase 1 (`7a56fde`). Built auth module (JWT RS256, rotating refresh cookies, RBAC, profile, addresses, password reset, admin customers, bootstrap admin), common security/rate-limit/audit, notification module (email), Mailpit. Fixed CHAR/CITEXT schema-validation mapping. `./mvnw verify` green (31 unit + 35 IT); local smoke test incl. Mailpit delivery. |
