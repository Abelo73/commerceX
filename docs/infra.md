# Infrastructure

Local development runs dependencies in Docker; the backend runs from the IDE / `./mvnw spring-boot:run`, the frontend with `npm run dev`.

## docker-compose services (added when their phase needs them)

File: `infra/docker-compose.yml`. Host ports are shifted from defaults to avoid clashing with other local stacks (5432, 6379, 1025, 8025, 9090, 3001 are already taken on the dev machine).

| Service | Image | Port | Added in |
|---|---|---|---|
| postgres | `postgres:17-alpine` | **5442** | Phase 1 |
| mailpit (dev SMTP + UI) | `axllent/mailpit` | **1026 / 8026** | Phase 2 |
| redis | `redis:7` | **6389** | Phase 9 (or earlier for rate limiting) |
| kafka (KRaft, no ZooKeeper) | `apache/kafka` | 9092 | Phase 9 |
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

Integration tests use **Testcontainers** (PostgreSQL, Redis, Kafka) — they do not depend on docker-compose.
