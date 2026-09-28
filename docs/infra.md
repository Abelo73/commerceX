# Infrastructure

Local development runs dependencies in Docker; the backend runs from the IDE / `./mvnw spring-boot:run`, the frontend with `npm run dev`.

## docker-compose services (added when their phase needs them)

File: `infra/docker-compose.yml`. Host ports are shifted from defaults to avoid clashing with other local stacks (5432, 6379, 1025, 8025, 9090, 3001 are already taken on the dev machine).

| Service | Image | Port | Added in |
|---|---|---|---|
| postgres | `postgres:17-alpine` | **5442** | Phase 1 |
| mailpit (dev SMTP + UI) | `axllent/mailpit` | **1026 / 8026** | Phase 2 |
| redis | `redis:7-alpine` | **6389** | Phase 9 ✅ (rate limiting, cache) |
| kafka (KRaft, no ZooKeeper) | `apache/kafka:3.9.1` | 9092 | Phase 9 ✅ (topics created by the app; auto-create off) |
| prometheus | `prom/prometheus` | **9091** | Phase 12 |
| grafana | `grafana/grafana` | **3002** | Phase 12 |
| backend | built from `backend/Dockerfile` | 8080 | Phase 12 (full-stack compose profile) |
| frontend | built from `frontend/Dockerfile` | 3000 | Phase 12 |

```text
Browser ──► frontend (Next.js :3000) ──► backend (Spring Boot :8080)
                                             ├── PostgreSQL  (source of truth)
                                             ├── Redis       (cache, rate limit)
                                             ├── Kafka       (async events via outbox)
                                             └── SMTP        (mailpit in dev)
Prometheus ◄── /actuator/prometheus ── backend
Grafana ◄── Prometheus
```

## Configuration

- Spring profiles: `local`, `test`, `prod`.
- Secrets via environment variables only; `.env.example` committed, `.env` git-ignored.
- JWT RSA keys: generated locally for dev; injected from secret store in prod.

## Tests

Integration tests use **Testcontainers** (PostgreSQL 17, Kafka 3.9.1, Redis 7) via `TestcontainersConfiguration` — they do not depend on docker-compose. All three start once per test JVM (shared Spring context).

## Degraded modes (verified manually, Phase 9)

| Dependency down | Behaviour |
|---|---|
| Kafka | Requests succeed; events wait in `outbox_events` (metric `commercex_outbox_pending`), retried with exponential backoff, delivered after recovery |
| Redis | Rate limiting falls back to in-memory per instance (`commercex_ratelimit_fallback_total`); cache misses go to PostgreSQL. ~1 s extra latency per request from 500 ms timeouts — circuit breaker planned (Phase 13) |
| SMTP | Consumer fails → retried 3× with backoff → `<topic>.DLT`; order flow unaffected |
