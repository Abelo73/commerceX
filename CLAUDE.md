# CLAUDE.md — CommerceX

Production-oriented e-commerce platform. Modular monolith (Spring Boot) + Next.js.

## Start of every session
1. Read `PROGRESS.md` → "Current Status" and "Next up".
2. Read the relevant doc in `docs/` before touching a module.
3. At the end: tick checkboxes, update Current Status, Decisions Log, Session Log in `PROGRESS.md`.

## Source of truth
- What to build: `PRD.md` · How: `TRD.md`
- Architecture & module rules: `docs/architecture.md`
- Schema: `docs/erd.md` · State machine, inventory, payments: `docs/domain-model.md`
- Endpoints & conventions: `docs/api.md` · Pages: `docs/pages.md` · Docker: `docs/infra.md`

## Commands
```bash
docker compose -f infra/docker-compose.yml up -d   # dependencies
cd backend && ./mvnw verify                        # unit (*Test) + Testcontainers integration (*IT) tests
cd backend && ./mvnw spring-boot:test-run          # run app on throwaway Testcontainers Postgres
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
python3 infra/seed/seed_catalog.py                # sample running catalog + FIRSTRUN coupon (local profile)
cd frontend && npm run dev                         # http://localhost:3000 (proxies /api to :8080)
```

## Gotchas
- VS Code's Java extension also compiles into `backend/target`; if Maven reports missing class files, run `./mvnw clean verify`.
- Postgres runs on host port **5442** (not 5432), Redis on **6389**, Kafka on 9092. Mailpit UI: http://localhost:8026. Integration tests start Postgres + Kafka + Redis containers (Docker required).
- Cross-module async work: write an integration event with `Outbox.append(...)` inside the business transaction (never call KafkaTemplate directly from business code). Kafka consumers must call `ProcessedEvents.markIfNew` first.
- `local` profile bootstraps admin `admin@commercex.local` / `CommerceX-Admin-2026!` and uses an ephemeral JWT key (tokens die on restart).
- New public endpoints must be added to `common/security/SecurityConfig` (deny-by-default).
- Mock payment tokens: `tok_success`, `tok_decline`, `tok_3ds`, `tok_pending`, `tok_timeout` (charges then times out), `tok_unavailable`. `commercex.payments.webhook-secret` (≥ 32 chars) is required at startup.
- Never call a payment provider inside a DB transaction: record (tx) → call → apply (tx), see `PaymentService`.
- Integration tests must extend `AbstractIntegrationTest` without extra context-changing annotations, so all share one context + container.

## Frontend rules
- Next.js 16: read `frontend/node_modules/next/dist/docs/` before using an API; `params`/`searchParams` are Promises.
- Catalog pages are Server Components using `serverGet` (fetch + revalidate); interactive state uses `useCart`/`useAuth` from `components/providers.tsx` and `api()` from `lib/client-api.ts` (never call the backend origin from the browser).
- Tokens live in `src/app/globals.css` (`@theme`); use them, not raw colours. One animated moment per page at most; respect reduced motion.

## Non-negotiable rules
- Build in phase order; one vertical slice at a time. Don't rewrite unrelated code.
- Module code only calls other modules via their `application` package or events. Never another module's repository.
- Controllers: no business logic. Repositories: no workflows. Transactions on application services.
- Never return JPA entities from controllers — records for DTOs, explicit mappers. Constructor injection, no Lombok.
- Every schema change = new Flyway migration. Never edit an applied migration.
- Money = `long` minor units + currency (`Money` value object). Never `double`.
- Never trust client prices/totals. Recalculate server-side.
- Inventory reserve = atomic conditional `UPDATE` (see domain-model.md). Reserve in ascending variant ID order.
- No external HTTP calls (payment provider) inside a DB transaction.
- `Idempotency-Key` on order creation, payment, refund; webhooks deduplicated by provider event ID.
- Validate all input (`@Valid`, Bean Validation). Authorize every endpoint (deny by default).
- Never log passwords, tokens, or payment data.
- Add a technology only when a real requirement needs it (TRD §23).

## Definition of done
Implementation + tests (unit, Testcontainers integration, concurrency where relevant) + validation + authorization + error handling + migration + OpenAPI up to date + no sensitive logging + `PROGRESS.md` updated.

For every critical flow, answer TRD §20: retry? DB down? payment ok but timeout? duplicate webhook? last-item race? Kafka down? Redis down? malicious input?
