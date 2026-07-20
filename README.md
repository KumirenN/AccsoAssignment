# Shipment Tracking Service

A **Spring Boot reference implementation** for ingesting courier webhooks, reconciling messy event streams, and exposing current shipment status with a full audit trail.

Built to demonstrate production-style Java backend work: layered architecture, domain-driven projection rules, partner-specific deduplication, schema migrations, ADRs, and comprehensive tests.

**Core capabilities:** event-id dedupe (`dhl`) · natural-key dedupe (`acme`) · forward-only status projection · full ingest audit

---

## What this project demonstrates

| Area | Highlights |
|------|------------|
| **Backend** | Java 17, Spring Boot 3, REST APIs, OpenAPI/Swagger |
| **Persistence** | JPA/Hibernate, H2 (PoC), Liquibase migrations |
| **Domain logic** | Pure `StateProjector` — out-of-order, duplicate, and conflict handling |
| **Integration patterns** | Webhook idempotency (HTTP 200 on duplicate), partner-specific dedupe strategies |
| **Architecture** | Clean layers (`api` → `application` → `domain` → `infrastructure`), strategy pattern for dedupe |
| **Documentation** | ADRs, ERD/class diagrams, architecture analysis, hands-on walkthrough |
| **Testing** | Unit tests for business rules, integration tests for full HTTP flows (29 tests) |
| **Process** | AI-assisted development with documented human overrides — see [`docs/DEVELOPMENT_PROCESS.md`](docs/DEVELOPMENT_PROCESS.md) |

---

## Quick start

**Prerequisites:** Java 17 only (Maven wrapper included).

```bash
git clone https://github.com/KumirenN/shipment-tracking-service.git
cd shipment-tracking-service
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
- **Hands-on:** [`docs/WALKTHROUGH.md`](docs/WALKTHROUGH.md) (copy-paste `curl` examples)

---

## Problem statement

Couriers send **messy** webhook updates: late, out of order, duplicated, and sometimes conflicting. Internal teams need:

1. **Current status** — what is true for each `shipmentId` right now.  
2. **History** — every ingest attempt, with enough context to explain how we chose the current status.

This is a focused microservice (not a full platform — no Kafka, auth, or UI). Scope: three HTTP APIs, clear integrity rules, auditability, and **extensible partner deduplication**.

**Approach:** append-only `shipment_event` audit + a projected `shipment` row; `StateProjector` applies forward-only status rules on business time (`occurredAt`). Deduplication is partner-specific via configuration.

*Expanded narrative:* [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §1–§2.

---

## Assumptions

| Topic | Assumption |
|-------|------------|
| Duplicate | `dhl`: same `(partner, eventId)`. `acme`: same `(partner, shipmentId, status, occurredAt)` — **`receivedAt` ignored for dedupe** |
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
| **Hibernate + Liquibase** | vs `ddl-auto=update` — reproducible schema; clean migration story for new partners |
| **200 on duplicate** | vs `409` — webhook-friendly idempotency |
| **SHA-256 on full raw JSON** for `payloadMismatch` | vs canonical subset — simple; retries with only `receivedAt` changed flag mismatch (documented) |

**ADRs:**

- [ADR 001 — Forward-only status projection](docs/adr/001-forward-only-shipment-status-projection.md)  
- [ADR 002 — Deduplication & database constraints](docs/adr/002-deduplication-strategy-and-database-constraints.md)

*More detail:* [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §7, §10 · [`docs/design/`](docs/design/README.md).

---

## Known limitations (PoC)

| Limitation | Notes |
|------------|--------|
| H2 in-memory | Data lost on restart; production → PostgreSQL |
| Single instance | No horizontal scaling / partitioning |
| No auth or rate limiting | Trusted webhook source assumed for PoC |
| Partner config in YAML | Redeploy to add partners |
| Natural-key collision | Same `(status, occurredAt)` cannot mean two different business events for `acme` |
| `stateExplanation` shows `event null` for `acme` | Accepted rows have no `eventId` — left visible for data-quality awareness |

*Full list:* [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §3.1.

---

## Multi-partner deduplication (Phase 2 extension)

**Scenario:** Partner `acme` has **no stable `eventId`** but may resend the same logical update with a new `receivedAt`.

| | |
|---|---|
| **What was added** | `application.yml` partner strategies; `DedupeStrategy` + `EventIdDedupeStrategy` / `NaturalKeyDedupeStrategy`; nullable `event_id`; Liquibase `003`–`004`; `NaturalKeyDedupeIntegrationTest`; walkthrough steps 11–16 |
| **What stayed the same** | Three APIs; response shapes; forward-only projection; full audit; `dhl` still requires `eventId` |
| **As-built note** | Natural-key dedupe lives in **application code** (see [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §6.4, §14) |
| **Production next steps** | Postgres, partial unique indexes, partner admin UI, monitoring |

---

## Tests

**Run:** `./mvnw test` (29 tests).

| Category | Test class | What it covers |
|----------|------------|----------------|
| **Business rules** | `StateProjectorTest` | Forward-only, RETURNED after DELIVERED, exception-wins, explanations |
| **Integration / E2E** | `ShipmentFlowIntegrationTest` | POST + GET; duplicates, out-of-order, conflict, invalid, 404 |
| **Multi-partner dedupe** | `NaturalKeyDedupeIntegrationTest` | `acme` without `eventId`; natural-key duplicate; `dhl` → `MISSING_EVENT_ID` |
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
| [`docs/ANALYSIS.md`](docs/ANALYSIS.md) | Full architecture analysis, business rules, Phase 2 extension, as-built notes |
| [`docs/DEVELOPMENT_PROCESS.md`](docs/DEVELOPMENT_PROCESS.md) | How the project was built (AI-assisted workflow + human overrides) |
| [`docs/WALKTHROUGH.md`](docs/WALKTHROUGH.md) | Copy-paste `curl` verification |
| [`docs/design/`](docs/design/README.md) | ERD + class diagram |
| [`docs/adr/`](docs/adr/) | Architecture decision records |

---

## Package layout

| Package | Role |
|---------|------|
| `com.shipment.tracking.api` | REST, DTOs, `ApiExceptionHandler` |
| `com.shipment.tracking.application` | Use cases, `application.dedupe` |
| `com.shipment.tracking.domain` | `StateProjector`, enums — no Spring |
| `com.shipment.tracking.infrastructure` | JPA, dedupe strategies, partner config, `PayloadHasher` |
| `com.shipment.tracking.config` | `DomainConfiguration` (`StateProjector` bean) |

Start at `IngestShipmentEventService`, then `StateProjector`.
