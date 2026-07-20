# Shipment tracking service

Spring Boot microservice portfolio example — ingest courier webhooks, project current shipment status, and expose audit history. **Phase 1** (`dhl`, event-id dedupe) + **Phase 2 change request** (`acme`, natural-key dedupe).

---

## Assignment deliverables (checklist)

| # | Requirement | Where |
|---|-------------|--------|
| 1 | Working solution + run in ≤5 min | [Quick start](#quick-start-5-minutes) below |
| 2 | README: framing, assumptions, trade-offs, limitations, change request | Sections below + [`docs/ANALYSIS.md`](docs/ANALYSIS.md) |
| 3 | 2 ADRs (decision, alternatives, why) | [`docs/adr/001`](docs/adr/001-forward-only-shipment-status-projection.md), [`002`](docs/adr/002-deduplication-strategy-and-database-constraints.md) |
| 4 | Tests: rules + integration + change request | [`Tests`](#tests) below |
| 5 | Development process note (AI use + override) | [`docs/DEVELOPMENT_PROCESS.md`](docs/DEVELOPMENT_PROCESS.md) |

---

## Quick start (≤5 minutes)

**Prerequisites:** Java 17 only (Maven wrapper included).

```bash
git clone https://github.com/KumirenN/AccsoAssignment.git
cd AccsoAssignment
./mvnw test          # Windows: .\mvnw.cmd test
./mvnw spring-boot:run
```

| Step | Time | Action |
|------|------|--------|
| 1 | ~2 min | Clone + `./mvnw test` (29 tests, H2 + Liquibase) |
| 2 | ~1 min | `./mvnw spring-boot:run` → http://localhost:8080 |
| 3 | ~2 min | Optional: one POST from [`docs/WALKTHROUGH.md`](docs/WALKTHROUGH.md) step 1 |

- **Swagger UI:** http://localhost:8080/swagger-ui.html  
- **OpenAPI:** http://localhost:8080/api-docs  
- **Hands-on:** [`docs/WALKTHROUGH.md`](docs/WALKTHROUGH.md) (Phase 1 + Phase 2 `curl` examples)

---

## Problem framing (how I interpreted the brief)

Couriers send **messy** webhook updates: late, out of order, duplicated, and sometimes conflicting. Internal teams need:

1. **Current status** — what is true for each `shipmentId` right now.  
2. **History** — every ingest attempt, with enough context to explain how we chose the current status.

The assignment is **not** a full platform (no Kafka, auth, or UI). Scope is three HTTP APIs, clear integrity rules, auditability, and a **change request** for a partner without a stable `eventId`.

**Approach:** append-only `shipment_event` audit + a projected `shipment` row; `StateProjector` applies forward-only status rules on business time (`occurredAt`). Deduplication is partner-specific (Phase 2).

*Expanded narrative:* [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §1–§2.

---

## Assumptions

| Topic | Assumption |
|-------|------------|
| Duplicate | Phase 1: same `(partner, eventId)`. Phase 2 (`acme`): same `(partner, shipmentId, status, occurredAt)` — **`receivedAt` ignored for dedupe** |
| Ordering | History sorted by `occurredAt` → `receivedAt` → row `id` |
| Status movement | Forward-only; late low-ordinal events do not roll back current status |
| After delivery | `RETURNED` allowed when `occurredAt` is after latest `DELIVERED`; **`RETURNED` is final** for that `shipmentId` |
| Same instant | `DELIVERY_EXCEPTION` wins over `DELIVERED` at the same `occurredAt` |
| Duplicates on POST | HTTP **200** + flags (`duplicate`, `payloadMismatch`, …) — not `409` |
| Invalid status | HTTP **400** but still persist `REJECTED_INVALID` audit row |
| Partner config | Server-side `application.yml`, not a field on the webhook |
| Persistence | H2 in-memory + Liquibase for this PoC |

*Full table with rationale:* [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §3.

---

## Design choices and trade-offs

| Choice | Why this over alternatives |
|--------|----------------------------|
| **Forward-only projection** (`StateProjector`) | vs last-write-wins on `receivedAt` — late events must not undo `DELIVERED` |
| **Full audit rows** (incl. duplicates) | vs skip duplicate storage — downstream/support need retry visibility |
| **DB UK + proactive dedupe** (`dhl`) | vs in-memory only — survives restart; race-safe backstop |
| **Application dedupe** (`acme`) | vs single global DB UK — full UK blocked `DUPLICATE` audit rows (H2 has no partial unique index) |
| **Hibernate + Liquibase** | vs `ddl-auto=update` — reproducible schema; Phase 2 migration story |
| **200 on duplicate** | vs `409` — webhook-friendly idempotency |
| **SHA-256 on full raw JSON** for `payloadMismatch` | vs canonical subset — simple; retries with only `receivedAt` changed flag mismatch (documented) |

**ADRs (decision + alternatives + why):**

- [ADR 001 — Forward-only status projection](docs/adr/001-forward-only-shipment-status-projection.md)  
- [ADR 002 — Deduplication & database constraints](docs/adr/002-deduplication-strategy-and-database-constraints.md)

*More detail:* [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §7, §10 · [`docs/design/`](docs/design/README.md).

---

## Known limitations (PoC)

| Limitation | Notes |
|------------|--------|
| H2 in-memory | Data lost on restart; production → PostgreSQL |
| Single instance | No horizontal scaling / partitioning |
| No auth or rate limiting | Webhooks trusted per brief |
| Partner config in YAML | Redeploy to add partners |
| Natural-key collision | Same `(status, occurredAt)` cannot mean two different business events for `acme` |
| `stateExplanation` shows `event null` for `acme` | Accepted rows have no `eventId` — left visible for data-quality awareness |

*Full list:* [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §3.1.

---

## Change request (Phase 2)

**Trigger:** Partner `acme` has **no stable `eventId`** but may resend the same logical update with a new `receivedAt`.

| | |
|---|---|
| **What changed** | `application.yml` partner strategies; `DedupeStrategy` + `EventIdDedupeStrategy` / `NaturalKeyDedupeStrategy`; nullable `event_id`; Liquibase `003`–`004` (drop natural-key UK); `ChangeRequestIntegrationTest`; walkthrough steps 11–16 |
| **What stayed the same** | Three APIs; response shapes; forward-only projection; full audit; `dhl` still requires `eventId` |
| **As-built note** | Natural-key dedupe lives in **application code** (see [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §6.4, §14) |
| **Production next** | Postgres, partial unique indexes, partner admin UI, monitoring |

---

## Tests

**Run:** `./mvnw test` (29 tests, 0 failures on 2026-05-19).

| Assignment ask | Test class | What it covers |
|----------------|------------|----------------|
| **Decision logic** (integrity rules) | `StateProjectorTest` | Forward-only, RETURNED after DELIVERED, exception-wins, explanations |
| **Integration / E2E** | `ShipmentFlowIntegrationTest` | POST + GET; duplicates, out-of-order, conflict, invalid, 404 |
| **Change request** | `ChangeRequestIntegrationTest` | `acme` without `eventId`; natural-key duplicate; `dhl` → `MISSING_EVENT_ID` |
| Smoke | `ShipmentTrackingApplicationTests` | Spring context + `main` |

Same scenarios as [`docs/WALKTHROUGH.md`](docs/WALKTHROUGH.md). Coverage: `./mvnw test jacoco:report` → `target/site/jacoco/index.html`.

---

## API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/shipment-events` | Ingest courier webhook |
| GET | `/shipments/{id}` | Current shipment + `stateExplanation` |
| GET | `/shipments/{id}/events` | Chronological audit (`occurredAt` → `receivedAt` → `id`) |

---

## Database (Liquibase)

- **H2:** `jdbc:h2:mem:shipmentdb`
- **Changelogs:** `src/main/resources/db/changelog/` — `001`–`004` (see [`docs/design/DATABASE_ERD.md`](docs/design/DATABASE_ERD.md))

| Partner | Strategy | Dedupe key |
|---------|----------|------------|
| `dhl` | `event-id` | `(partner, eventId)` — required |
| `acme` | `natural-key` | `(partner, shipmentId, status, occurredAt)` — `eventId` optional |

---

## Documentation map

| Doc | Purpose |
|-----|---------|
| [`docs/ANALYSIS.md`](docs/ANALYSIS.md) | Full analysis, rules §7, Phase 2, as-built deltas §14 |
| [`docs/DEVELOPMENT_PROCESS.md`](docs/DEVELOPMENT_PROCESS.md) | **AI process note** (required) |
| [`docs/WALKTHROUGH.md`](docs/WALKTHROUGH.md) | Copy-paste `curl` verification |
| [`docs/design/`](docs/design/README.md) | ERD + class diagram |
| [`docs/adr/`](docs/adr/) | Two ADRs |

---

## Package layout

| Package | Role |
|---------|------|
| `api` | REST, DTOs, `ApiExceptionHandler` |
| `application` | Use cases, `application.dedupe` |
| `domain` | `StateProjector`, enums — no Spring |
| `infrastructure` | JPA, dedupe strategies, partner config, `PayloadHasher` |
| `config` | `DomainConfiguration` (`StateProjector` bean) |

Start at `IngestShipmentEventService`, then `StateProjector`.
