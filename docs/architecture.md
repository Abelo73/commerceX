# Architecture

CommerceX is a **modular monolith**: one Spring Boot application, one PostgreSQL database, strict module boundaries. Modules can be extracted into services later if independent scaling or deployment is ever justified (TRD §23).

## Repository layout

```text
commercex/
├── backend/            Spring Boot app (Maven, Java 21)
├── frontend/           Next.js storefront + admin (App Router, TypeScript)
├── infra/              docker-compose, Prometheus/Grafana config, scripts
├── docs/               architecture, ERD, domain model, API, pages, infra
├── PRD.md / TRD.md     specs
├── PROGRESS.md         build tracker
└── CLAUDE.md           working rules for Claude
```

## Backend modules

Base package: `com.commercex`

| Module | Owns |
|---|---|
| `common` | Money, error handling, pagination, correlation IDs, idempotency, outbox, security utils |
| `auth` | Users, roles, credentials, JWT, refresh tokens, password reset, addresses |
| `catalog` | Products, variants, options, images, tags, brands, search |
| `category` | Category tree |
| `inventory` | Stock levels, reservations, movements ledger, low-stock |
| `cart` | Carts and cart items. Public: `CartApi` (used by wishlist, checkout) |
| `wishlist` | Wishlist items |
| `checkout` | Checkout session orchestration: pricing, shipping, order placement |
| `order` | Orders, order items, state machine, status history |
| `payment` | Payments, `PaymentProvider` adapters, webhooks, refunds |
| `promotion` | Coupons, redemption rules |
| `review` | Reviews and moderation |
| `notification` | Channel abstraction (email → SMS/push), event consumers |
| `admin` | Dashboard read models / KPIs (reads only; mutations go through owning modules) |

## Inside a module

```text
com.commercex.order
├── api/              @RestController + request/response DTOs (records) + mappers
├── application/      use-case services, @Transactional boundaries, the module's public API
├── domain/           JPA entities, value objects, state machine, domain rules
└── infrastructure/   Spring Data repositories, external adapters
```

Layering (TRD §4): `api → application → domain ← infrastructure`.

- Controllers: parse/validate input, call one application service, map to response DTO. No business logic.
- Application services: own the transaction, orchestrate domain + repositories.
- Domain: business rules (state transitions, pricing math, invariants).
- Repositories: data access only, never workflows.
- **JPA entities never leave a module and are never serialized to HTTP.**

## Module boundary rules

1. A module's **public API is the set of types in its base package** (e.g. `com.commercex.category.CategoryApi`, `com.commercex.auth.PasswordResetRequested`). Sub-packages (`api`, `application`, `domain`, `infrastructure`) are internal — Spring Modulith fails the build if another module touches them. Other modules call the public interface or consume published events.
2. No module reads another module's repositories or tables.
3. Cross-module references are by ID (e.g. `order_items.variant_id`), not JPA associations.
4. Asynchronous side effects (notifications, analytics) go through domain events → outbox → Kafka (Phase 9). Before Kafka exists, in-process `ApplicationEventPublisher` + `@TransactionalEventListener` is used.
5. Enforced by **Spring Modulith** `ApplicationModules.of(...).verify()` in a test.

## Key technical decisions

| Topic | Decision | Why |
|---|---|---|
| Build | Maven, single module `backend/` | Simple; Java packages give module boundaries, Modulith verifies them |
| Framework | Spring Boot 3.x, Java 21 | TRD §1 |
| Primary keys | `BIGINT` from per-table sequences (`allocationSize = 50`) | Allows Hibernate insert batching; public URLs use slugs / order numbers |
| Money | `BIGINT` minor units + `CHAR(3)` currency; `Money` value object | No floating point, no rounding surprises |
| Auth | Self-issued JWT (RS256) via Spring Security resource server; opaque rotating refresh tokens in httpOnly cookie | No extra infra; Keycloak/OIDC only if needed later |
| Inventory concurrency | Atomic conditional `UPDATE … WHERE on_hand - reserved >= :qty` + DB `CHECK` constraints | Single round trip, no lock held across app code, cannot oversell even under retries — see [domain-model.md](domain-model.md#inventory) |
| Errors | RFC 7807 `ProblemDetail` | Standard, supported natively by Spring |
| Idempotency | `idempotency_keys` table in PostgreSQL (Redis only as optional fast path) | Correctness must survive Redis outage |
| Events | Transactional outbox table → publisher → Kafka | Avoids "DB committed but Kafka failed" |
| Search | PostgreSQL full-text (`tsvector` + GIN) behind a `ProductSearch` interface | OpenSearch can replace it later |
| Frontend | One Next.js app; storefront and `/admin` as separate route groups/layouts | One deploy, shared design system |

## Failure-thinking checklist

Every critical feature is reviewed against TRD §20: retries, DB failure, payment-succeeded-but-timeout, duplicate webhooks, last-item races, Kafka down, Redis down, malicious input.
