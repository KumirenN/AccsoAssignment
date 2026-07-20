# ADR 002: Deduplication strategy and database constraints

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-18 |
| **Deciders** | Developer |

## Context

Shipment events arrive via webhook. Partners may:

- Retry the **same `eventId`** (phase 1 — e.g. DHL).
- Resend the **same logical update** without a stable `eventId`, with different `receivedAt` (Phase 2 — e.g. partner `acme`).

The design prioritises **duplicates, ordering, and auditability** over feature breadth.

## Decision

### 1. Dedupe by phase

| Phase | Implementation | Unique key |
|-------|----------------|------------|
| **Phase 1 (Commit 1)** | Single path: insert + `uk_partner_event_id`; `eventId` required | `(partner, event_id)` |
| **Phase 2 (Commit 2)** | Strategy pattern + `application.yml` partner config | event-id: `(partner, event_id)` · natural-key: `(partner, shipment_id, status, occurred_at)` |

**Phase 2 classes** (`DedupeStrategy`, `NaturalKeyDedupeStrategy`, `PartnerConfigProperties`) are **not** in Phase 1 code — see `docs/ANALYSIS.md` §5–6 and `docs/design/CLASS_DIAGRAM.md`.

Partner behaviour is configured in **`application.yml`** (not on the inbound webhook body). Ingest resolves strategy by `partner` code in Phase 2.

`receivedAt` is **never** used for deduplication.

### 2. Enforce uniqueness in the database (Liquibase + H2)

- **`shipment_event.id`:** `bigint` **auto-increment** (IDENTITY) surrogate PK in Liquibase `002`; JPA `GenerationType.IDENTITY`. Unchanged in Phase 2 — simplifies audit row inserts and history tie-break (`occurred_at`, `received_at`, `id`).
- Phase 1 migration: unique constraint on `(partner, event_id)`.
- Phase 2 migration: nullable `event_id`; natural-key UK was tried in `003` and **dropped in `004`** (see `ANALYSIS.md` §6.4) — natural-key dedupe is application-enforced for the PoC.

Event-id partners: proactive duplicate check + `uk_partner_event_id` backstop; duplicate audit rows use synthetic `event_id`. HTTP **200** on duplicate (not `409`).

### 3. Full-row audit for every ingest attempt

Persist **every** ingest (accepted, duplicate, invalid) with:

- Normalised columns
- **Full raw JSON** payload
- `disposition` for history API

Duplicates store the **complete row** even when payload duplicates an earlier accept—trade-off: storage vs 100% consumer visibility (accepted; duplicate rate expected low).

### 4. Same idempotency key, different body (industry practice)

- Do **not** overwrite the first accepted event.
- Persist duplicate row; set `payloadMismatch: true` on POST response when SHA-256 of the **full raw JSON** differs from the first accepted row for that dedupe key; structured **WARN** log.
- First accepted payload remains canonical for state projection.
- **Dedupe key vs hash:** Natural-key dedupe ignores `receivedAt`, but `receivedAt` is still in the raw payload — a retry with only `receivedAt` changed is a **duplicate** with `payloadMismatch: true` (walkthrough Phase 1 step 3, Phase 2 step 12).

### 5. Partner-facing vs consumer-facing responses

- **`POST /shipment-events`:** Booleans only (`accepted`, `duplicate`, `payloadMismatch`, `stateChanged`, …).
- **`GET /shipments/{id}/events`:** All dispositions visible to downstream consumers.

### Why we chose this

The design prioritises **auditability** and **idempotent webhooks**. Persisting every attempt with DB-backed keys (where possible) gives a defensible source of truth for support tools and integration tests, while HTTP 200 on duplicates avoids partner retry storms. Phase 2 extends the same pipeline with partner-specific keys without forking the ingest API.

## Alternatives considered

| Alternative | Why not chosen |
|-------------|----------------|
| **In-memory `Set` of seen keys** | Lost on restart; not testable as integration truth. |
| **`ddl-auto=update` without Liquibase** | Weaker reproducibility; chose Liquibase for schema-as-code on startup. |
| **Dedupe only in application layer** | Rejected for **event-id** partners (race-prone); **hybrid** for PoC — DB UK for `dhl`, application layer for `acme` (§6.4). |
| **Skip persisting duplicate rows** | Hides retries from consuming services; rejected for audit visibility. |
| **`409 Conflict` on duplicate** | Partners often retry non-2xx; **200** idempotent response is webhook-friendly. |
| **Inbound `partnerProvidesEventId` flag** | Pollutes partner contract; server-side config only. |
| **Partial unique indexes per partner** | Single-table constraints sufficient for PoC scale. |

## Consequences

### Positive

- Phase 2 extension (no `eventId`) extends design without rewriting ingest pipeline.
- Dedupe survives process restart (within H2 session; production would use PostgreSQL).
- Audit trail supports support and incident analysis.

### Negative

- Two unique constraints; natural-key partners cannot send distinguishable events with identical `(status, occurredAt)`—document as partner contract assumption.
- Storing full duplicate payloads increases storage (accepted trade-off).
- Partner config in YAML requires redeploy to add partners (production: external config service).

## Implementation reconciliation (deltas from initial ADR)

Original decision text above is **retained**. As-built adjustments:

| Initial ADR implication | As implemented | How we picked it up |
|-------------------------|----------------|---------------------|
| Phase 2 DB UK on natural key + optional `DataIntegrityViolationException` path | UK **removed** in Liquibase `004`; natural-key dedupe **proactive in service** | `NaturalKeyDedupeIntegrationTest` step 12 (`23505`) |
| “Two unique constraints” (Consequences) | **One** effective UK: `(partner, event_id)` for event-id partners only | Schema review after `004` |
| Duplicate storage always `{eventId}::dup::` | **`acme`:** `nk::…::dup::` token; accepted rows may have **null** `event_id` | GET history during walkthrough |
| Alternatives: “Dedupe only in application layer” rejected | **Hybrid:** DB UK for `dhl`, application layer for `acme` | §6.4 / this reconciliation |

Full table: [`docs/ANALYSIS.md`](../ANALYSIS.md) §14.

## References

- `docs/ANALYSIS.md` §7.1, §6, §14
