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
| **Current phase** | Phase 1 — Spring Boot Foundation (next) |
| **Current task** | Phase 0 complete — ready to scaffold backend |
| **Last updated** | 2026-09-28 |
| **Repo state** | Git initialized (`main`, no commits yet). Specs + docs + CLAUDE.md. No application code yet |
| **Blockers** | None |

### Next up
1. Initial git commit (awaiting user go-ahead)
2. Scaffold Spring Boot 3.x / Java 21 Maven project in `backend/` (Phase 1)
3. `infra/docker-compose.yml` with PostgreSQL, Flyway baseline, error handling, Actuator, OpenAPI, Testcontainers base test

---

## Phase Overview

| # | Phase | Status |
|---|---|---|
| 0 | Product + Architecture | `[x]` |
| 1 | Spring Boot Foundation | `[ ]` |
| 2 | Authentication + Users | `[ ]` |
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
- [ ] Initial commit (awaiting user go-ahead)
- [x] Repository layout (`backend/`, `frontend/`, `infra/`, `docs/`)
- [x] [`docs/architecture.md`](docs/architecture.md) — module map, module boundaries, layering rules
- [x] [`docs/erd.md`](docs/erd.md) — PostgreSQL ERD (users, products, variants, inventory, carts, orders, payments, coupons, reviews, outbox)
- [x] [`docs/domain-model.md`](docs/domain-model.md) — aggregates, order state machine, inventory reservation flow
- [x] [`docs/api.md`](docs/api.md) — REST endpoint outline per module (pagination, error format, idempotency headers)
- [x] [`docs/pages.md`](docs/pages.md) — storefront + admin page map
- [x] [`docs/infra.md`](docs/infra.md) — Docker Compose architecture (app, postgres, redis, kafka)
- [x] [`CLAUDE.md`](CLAUDE.md) — working rules for Claude derived from TRD §19–22

## Phase 1 — Spring Boot Foundation

- [ ] Spring Boot 3.x / Java 21 project in `backend/`
- [ ] Package structure: `common` + one package per module (auth, catalog, category, inventory, cart, checkout, order, payment, promotion, review, wishlist, notification, admin)
- [ ] `docker-compose.yml` with PostgreSQL (Redis/Kafka added when needed)
- [ ] Flyway wired, `V1__baseline.sql`
- [ ] Global error handling (RFC 7807 Problem Details) + validation errors
- [ ] Correlation ID filter + structured logging
- [ ] Actuator: health, info, metrics
- [ ] OpenAPI / Swagger UI
- [ ] Testcontainers base integration test class
- [ ] Module boundary test (Spring Modulith `verify()`)

## Phase 2 — Authentication + Users

- [ ] `users` + `roles` migrations
- [ ] Register / login / refresh / logout
- [ ] Password hashing (BCrypt via `DelegatingPasswordEncoder`)
- [ ] JWT (RS256) issuance + validation; rotating refresh tokens in httpOnly cookie
- [ ] RBAC: CUSTOMER, ADMIN, CATALOG_MANAGER, OPERATIONS
- [ ] Customer profile + addresses
- [ ] Password reset flow (token-based)
- [ ] Rate limiting on auth endpoints
- [ ] Audit log for security events
- [ ] Tests: authN, authZ per role, validation

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

---

## Session Log

| Date | Phase | Work done |
|---|---|---|
| 2026-09-28 | 0 | Read PRD + TRD. Created PROGRESS.md tracker. |
| 2026-09-28 | 0 | Decisions D-02..D-11 made. git init + .gitignore. Wrote docs/ (architecture, erd, domain-model, api, pages, infra) and CLAUDE.md. Phase 0 done. |
