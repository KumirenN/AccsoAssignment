# ADR 002: Deduplication strategy and database constraints

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-18 |
| **Deciders** | Candidate |

## Context

Shipment events arrive via webhook. Partners may:

- Retry the **same `eventId`** (phase 1 — e.g. DHL).
- Resend the **same logical update** without a stable `eventId`**, with different `receivedAt`** (change request — e.g. partner `acme`).

The assignment emphasises **duplicates, ordering, and auditability** over feature breadth.

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

- Phase 1 migration: unique constraint on `(partner, event_id)`.
- Phase 2 migration: nullable `event_id`; unique constraint on `(partner, shipment_id, status, occurred_at)`.

On conflict, catch `DataIntegrityViolationException` → disposition `DUPLICATE`, HTTP **200**, partner response `duplicate: true`.

### 3. Full-row audit for every ingest attempt

Persist **every** ingest (accepted, duplicate, invalid) with:

- Normalised columns
- **Full raw JSON** payload
- `disposition` for history API

Duplicates store the **complete row** even when payload duplicates an earlier accept—trade-off: storage vs 100% consumer visibility (accepted; duplicate rate expected low).

### 4. Same idempotency key, different body (industry practice)

- Do **not** overwrite the first accepted event.
- Persist duplicate row; set `payloadMismatch: true` on POST response; structured **WARN** log.
- First accepted payload remains canonical for state projection.

### 5. Partner-facing vs consumer-facing responses

- **`POST /shipment-events`:** Booleans only (`accepted`, `duplicate`, `payloadMismatch`, `stateChanged`, …).
- **`GET /shipments/{id}/events`:** All dispositions visible to downstream consumers.

## Alternatives considered

| Alternative | Why not chosen |
|-------------|----------------|
| **In-memory `Set` of seen keys** | Lost on restart; not testable as integration truth. |
| **`ddl-auto=update` without Liquibase** | Weaker reproducibility; chose Liquibase for schema-as-code on startup. |
| **Dedupe only in application layer** | Race-prone under concurrency; DB constraint is source of truth. |
| **Skip persisting duplicate rows** | Hides retries from consuming services; rejected for audit visibility. |
| **`409 Conflict` on duplicate** | Partners often retry non-2xx; **200** idempotent response is webhook-friendly. |
| **Inbound `partnerProvidesEventId` flag** | Pollutes partner contract; server-side config only. |
| **Partial unique indexes per partner** | Single-table constraints sufficient for assignment scale. |

## Consequences

### Positive

- Change request (no `eventId`) extends design without rewriting ingest pipeline.
- Dedupe survives process restart (within H2 session; production would use PostgreSQL).
- Audit trail supports support and incident analysis.

### Negative

- Two unique constraints; natural-key partners cannot send distinguishable events with identical `(status, occurredAt)`—document as partner contract assumption.
- Storing full duplicate payloads increases storage (accepted trade-off).
- Partner config in YAML requires redeploy to add partners (production: external config service).

## References

- `docs/ANALYSIS.md` §4.2, §4.6, §5
- Assignment brief: duplicate / audit / change-request requirements
