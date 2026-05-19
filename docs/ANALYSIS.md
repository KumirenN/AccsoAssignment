# Accso Technical Assignment — Analysis

**Purpose:** Show we understand the client problem, how we solve it, what was unclear, and what we assumed.  
**Phases:** **Phase 1** = first git commit (full initial solution). **Phase 2** = second commit (change request only).  
**Design detail:** [`docs/design/`](design/README.md) (ERD + UML, split by phase)

---

## 1. The client problem (from the brief)

Couriers send shipment updates by webhook. Those updates are messy:

- They arrive **late**
- They arrive **out of order**
- The same update is sent **more than once**
- Two updates can **conflict** (same time, different status)

Support, tracking, and incident teams need:

1. **Current status** — what is true *right now* for each shipment?
2. **History** — what happened, and how did we decide the current status?

We are **not** building a big platform (no Kafka, no auth stack, no frontend).

---

## 2. How our service solves it

| Client need | Assignment API | Our approach |
|-------------|----------------|--------------|
| Ingest webhooks | `POST /shipment-events` | Save every attempt; detect duplicates; update status with clear rules |
| Current view | `GET /shipments/{shipmentId}` | One row per shipment with current status, when it happened, event count, explanation |
| History | `GET /shipments/{shipmentId}/events` | All ingest rows in order, with disposition (accepted, duplicate, etc.) |
| Duplicates | Data integrity #1 | DB unique key + full duplicate row in audit |
| Out-of-order | Data integrity #2 | Use `occurredAt` (not `receivedAt`); status only moves **forward** |
| Conflicts | Data integrity #3 | Fixed status order; `DELIVERY_EXCEPTION` wins over `DELIVERED` at same time |

**Statuses we support** (from brief):  
`LABEL_CREATED` → `HANDED_TO_CARRIER` → `IN_TRANSIT` → `OUT_FOR_DELIVERY` → `DELIVERED` → `RETURNED` → `DELIVERY_EXCEPTION` (highest for same-time conflicts).

---

## 3. Known ambiguities, assumptions, and why

The brief leaves some details open. Below is what we chose and **why**.

| What was unclear | Our assumption | Why |
|------------------|----------------|-----|
| What counts as a **duplicate**? | Phase 1: same `(partner, eventId)`. Phase 2: for some partners, same `(partner, shipmentId, status, occurredAt)` | Matches how partners resend; `receivedAt` can change on retry (Phase 2 brief) |
| Order events for history? | `occurredAt` ↑, then `receivedAt` ↑, then row id | Business time matters more than when we received it |
| Can status go **backward**? | **No** for current status; late old events stay in history only | Stops a late `IN_TRANSIT` from undoing `DELIVERED` |
| Can you **return** after **delivered**? | **Yes**, if return `occurredAt` is after delivery | Real returns happen after delivery |
| Is **`RETURNED` terminal** for this shipment? | **Yes** — treat as **final** courier lifecycle status | Once returned, the order is complete from the courier’s perspective; a **new shipment** (new `shipmentId`) is required to send goods again — we do not model “re-ship on same id” |
| `DELIVERED` vs `DELIVERY_EXCEPTION` at same time? | Keep **EXCEPTION** | Surfaces problems instead of hiding them |
| Store **duplicate** payloads? | **Yes**, full JSON row | Downstream can see retries; storage cost is low |
| HTTP code on duplicate? | **200** | Webhooks often retry on errors |
| Do partners read our POST body? | **No** — simple flags only (`accepted`, `duplicate`, …) | Brief focuses on ingest, not partner UX |
| Invalid status? | **400** but still **save** row as `REJECTED_INVALID` | Audit trail for bad data |
| `stateExplanation` always? | **Yes** on GET shipment | Helps support without reading all events |
| Where is partner dedupe config? | **Our `application.yml`**, not on webhook JSON | We know our partners; don’t change their payload |
| Database for assignment? | **H2 in-memory** + **Liquibase** | Clone and run in minutes; dedupe via DB constraints |
| Phase 2 in Phase 1 code? | **No** — see §5 and §6 | Clean commits: Phase 1 complete, Phase 2 is only the change |

### 3.1 Known limitations (PoC scope)

Deliberate boundaries for this assignment — not gaps we forgot to mention in code:

| Limitation | Notes |
|------------|--------|
| **H2 in-memory** | Data lost on restart; not production persistence (Postgres + migrations would be next) |
| **Single instance** | No clustering, partition keys, or horizontal ingest scaling |
| **No auth / rate limiting** | Webhooks trusted as in the brief |
| **No message bus** | Synchronous HTTP ingest only |
| **Partner config in yaml** | No admin UI to change dedupe strategy at runtime (Phase 2) |
| **`RETURNED` is final** | No further *business* progression on the same `shipmentId` after `RETURNED` (see §7.3.1); late lower-ordinal events are audit-only |
| **Phase 2 not in Commit 1** | Natural-key partner (`acme`) ships only in Commit 2 |

Production gaps after the change request are summarized in §6.3.

---

## 4. Git and delivery plan

| Commit | Phase | What it contains |
|--------|-------|------------------|
| **Commit 1** | **Phase 1** | Full working service: 3 APIs, eventId dedupe, integrity rules, tests (no change-request partner) |
| **Commit 2** | **Phase 2** | **All** change-request work: natural-key dedupe, partner config, schema migration, new tests |

Do **not** ship Phase 2 classes, config, or DB constraints in Commit 1.

---

## 5. Phase 1 — Initial solution (Commit 1)

**Goal:** Solve duplicates, out-of-order, and conflicts for partners that send a stable **`eventId`**.

### 5.1 Phase 1 — In scope

| Item | Detail |
|------|--------|
| APIs | `POST /shipment-events`, `GET /shipments/{id}`, `GET /shipments/{id}/events` |
| Dedupe | `(partner, event_id)` unique in database; **`eventId` required** on ingest |
| Audit | Every ingest stored (accepted, duplicate, invalid) with full JSON |
| Status rules | Forward-only + return-after-delivery + exception-wins (§7) |
| Tests | Unit tests for rules; integration test for happy path, duplicate, out-of-order, conflict |
| Docs | README, 2 ADRs, `DEVELOPMENT_PROCESS.md`, OpenAPI, this analysis |
| Stack | Java 17, Spring Boot 3, Maven, JPA, H2, Liquibase, structured logging |

### 5.2 Phase 1 — Explicitly NOT in code

| Item | Comes in Phase 2 |
|------|------------------|
| `NaturalKeyDedupeStrategy` | Yes |
| `DedupeStrategy` / resolver | Yes (introduced when we add second strategy) |
| `PartnerConfigProperties` / `acme` partner | Yes |
| `uk_partner_natural_key` DB constraint | Yes |
| Nullable / optional `eventId` for ingest | Yes |
| Natural-key integration tests | Yes |

**Phase 1 dedupe implementation:** Check `existsByPartnerAndEventId` before insert; on duplicate → full audit row with synthetic `event_id` (`::dup::` suffix) and HTTP 200. The DB unique key `uk_partner_event_id` remains as a safety net. No strategy interface yet.

### 5.3 Phase 1 — Liquibase

- `001` — `shipment` table  
- `002` — `shipment_event` table, `event_id` **NOT NULL**, `uk_partner_event_id`, timeline index  

See [`docs/design/DATABASE_ERD.md`](design/DATABASE_ERD.md) § Phase 1.

### 5.4 Phase 1 — Assignment checklist

- [x] Persist events  
- [x] Duplicate rule documented and tested (`eventId`)  
- [x] Audit trail (all attempts, dispositions on history API)  
- [x] Clear POST response (`accepted`, `duplicate`, `stateChanged`, …)  
- [x] GET current state (all required fields + always `stateExplanation`)  
- [x] GET history (ordered, shows disposition)  
- [x] Document duplicate / out-of-order / conflict rules  

---

## 6. Phase 2 — Change request (Commit 2)

**Trigger (assignment):** A new partner has **no stable `eventId`**. They resend the same update with different **`receivedAt`**. We must still dedupe and keep audit.

**Goal:** Add second dedupe rule **without** breaking Phase 1 partners.

### 6.1 Phase 2 — What we add (all changes in this commit)

| Change | Detail |
|--------|--------|
| Partner config | `application.yml`: e.g. `dhl` = event-id, `acme` = natural-key |
| Code | `DedupeStrategy`, `EventIdDedupeStrategy`, `NaturalKeyDedupeStrategy`, `DedupeStrategyResolver`; refactor ingest to use resolver |
| Dedupe rule | `(partner, shipment_id, status, occurred_at)` — **ignore `receivedAt`** |
| `eventId` | **Null or omitted** allowed for natural-key partners only |
| Liquibase `003` | `event_id` nullable; `uk_partner_natural_key` |
| Tests | Change-request scenario (resend same update, different `receivedAt` → duplicate) |
| README | What changed, what stayed same, production gaps |

### 6.2 Phase 2 — What stays the same

- All three APIs and response shapes  
- Forward-only status rules (§7)  
- Full-row audit and dispositions  
- Phase 1 partners (`dhl`) still use `eventId` dedupe  

### 6.3 Phase 2 — README must answer (assignment)

- What changed in code / model / rules  
- What stayed the same and why  
- What we would still do for production (partner admin UI, Postgres, monitoring, etc.)  

See [`docs/design/DATABASE_ERD.md`](design/DATABASE_ERD.md) and [`docs/design/CLASS_DIAGRAM.md`](design/CLASS_DIAGRAM.md) § Phase 2.

---

## 7. Business rules (both phases)

These apply in **Phase 1 and Phase 2**. Only **dedupe** differs by phase/partner.

### 7.1 Duplicates (shared behaviour)

- Save **full payload** for duplicates (`DUPLICATE` disposition).  
- Do not change current status on duplicate.  
- Return **200** with `duplicate: true`.  
- Same key, different body → `payloadMismatch: true`, keep first accept, WARN log.

**Dedupe key (varies by phase/partner):**

| Phase | Partner type | Key |
|-------|--------------|-----|
| 1 | All | `(partner, eventId)` |
| 2 | event-id (e.g. dhl) | `(partner, eventId)` |
| 2 | natural-key (e.g. acme) | `(partner, shipmentId, status, occurredAt)` |

### 7.2 Out-of-order

- Accept if not duplicate.  
- Recompute status using `occurredAt` order.  
- If new status is not “forward” vs current → save event, disposition `ACCEPTED_NO_STATE_CHANGE`, status unchanged.

### 7.3 Conflicts (same `occurredAt`, different status)

- Use status order (§2).  
- `DELIVERY_EXCEPTION` beats `DELIVERED` at the same instant.

### 7.3.1 `RETURNED` as final status

We treat **`RETURNED` as the terminal business status** for a shipment id:

- **Presumption:** Once a parcel is returned, the courier’s job on **this** shipment is complete. If the merchant sends goods again, that is a **new order / new `shipmentId`**, not a continuation of the returned lifecycle.
- **Projection:** After current status is `RETURNED`, later events (including `DELIVERY_EXCEPTION` or lower-ordinal statuses) are stored in the audit log but do **not** change current status (`ACCEPTED_NO_STATE_CHANGE` where applicable). This matches “order complete” semantics without inventing extra statuses.
- **Contrast with `DELIVERED`:** `DELIVERED` is **not** terminal — we explicitly allow `RETURNED` after delivery when `occurredAt` is later (§3, ADR 001, `DEVELOPMENT_PROCESS.md` override example).

### 7.4 Invalid payload

- HTTP **400**, row saved as `REJECTED_INVALID`, no status update.

### 7.5 Event count (`processedEventCount`)

- Count: `ACCEPTED`, `ACCEPTED_STATE_CHANGED`, `ACCEPTED_NO_STATE_CHANGE`  
- Do **not** count: `DUPLICATE`, `REJECTED_INVALID`

### 7.6 Disposition (for consumers, not couriers)

| Value | Meaning |
|-------|---------|
| `ACCEPTED` / `ACCEPTED_STATE_CHANGED` / `ACCEPTED_NO_STATE_CHANGE` | Accepted ingest |
| `DUPLICATE` | Dedupe blocked it |
| `REJECTED_INVALID` | Bad status or validation |

Shown on **GET history**; **not** on POST response to partners.

### 7.7 `stateExplanation` — how we generate it (GET shipment)

**Assignment wording:** *“A brief explanation of why this is the current state, if it’s not obvious from your model.”*  
**Our choice:** Always return `stateExplanation` (not only when “non-obvious”) so support and downstream tools never guess.

**When it is built:** On every ingest that updates the `shipment` row (inside `StateProjector` + ingest service, same transaction as projection).

**How it is built:** Template text from the **last ingest that changed** (or tried to change) current status:

| Situation | Example explanation |
|-----------|---------------------|
| First status for shipment | `Current status IN_TRANSIT from event evt-1 (partner dhl) at 2026-03-10T12:00:00Z.` |
| Normal forward update | `Updated to DELIVERED from event evt-3 at 2026-03-10T18:00:00Z (was OUT_FOR_DELIVERY).` |
| Out-of-order, no change | `Status remains DELIVERED. Event evt-late (IN_TRANSIT at 2026-03-10T11:00:00Z) accepted for audit but did not move status backward.` |
| Same `occurredAt` conflict | `Status DELIVERY_EXCEPTION at 2026-03-10T18:00:00Z; preferred over DELIVERED at same time (exception wins).` |
| Return after delivery | `Status RETURNED at 2026-03-12T10:00:00Z after DELIVERED at 2026-03-10T18:00:00Z.` |

**Storage:** Persist on `shipment.state_explanation` at write time (denormalized). GET reads the stored string — no heavy recompute on read.

**Implementation:** `StateProjector.buildExplanation(before, after, triggeringEvent, disposition)` — unit-tested with one case per row above.

*Identified during validation of AI analysis — see [`DEVELOPMENT_PROCESS.md`](DEVELOPMENT_PROCESS.md) §6 (audit trail).*

### 7.8 Event history ordering (GET events)

**Assignment wording:** *“Show events in a clear order (explain your ordering choice).”*

**Our order (chronological story for humans):**

1. `occurred_at` **ASC** — business time: what happened first in the real world  
2. `received_at` **ASC** — if two events share the same `occurredAt`, order by when we received them  
3. `id` **ASC** — stable tie-break for identical timestamps  

**Why `occurredAt` first (not `receivedAt`)?**  
Consumers of this API are internal teams (support, tracking, incidents). They need an **audit timeline that matches how the shipment actually progressed**, not the order webhooks arrived. Chronological `occurredAt` makes duplicates, late events, and conflicts easy to read as a single narrative.

**What we return per row:** All dispositions (`ACCEPTED`, `DUPLICATE`, `REJECTED_INVALID`, etc.) so the audit shows retries and bad payloads, not only “happy path” events.

**Query:** `ShipmentEventRepository.findByShipmentIdOrderByOccurredAtAscReceivedAtAscIdAsc(shipmentId)` (matches DB index `idx_event_shipment_timeline`).

*Identified during validation of AI analysis — see [`DEVELOPMENT_PROCESS.md`](DEVELOPMENT_PROCESS.md) §6 (audit trail).*

---

## 8. API summary (both phases)

**POST `/shipment-events`** — response to partner:

`accepted`, `duplicate`, `payloadMismatch`, `stateChanged`, `currentStatus`, `shipmentId`, `eventId`

**GET `/shipments/{shipmentId}`:**

`shipmentId`, `currentStatus`, `statusOccurredAt`, `processedEventCount`, `stateExplanation` (always), `location`

**GET `/shipments/{shipmentId}/events`:**

All rows, all dispositions, chronological order per §7.8

Errors: custom JSON (`code`, `message`, fields).

---

## 9. Tests and hands-on walkthrough

**Assignment:** Automated tests for (1) **decision logic**, (2) **integration / E2E path**, (3) **change request scenario**.

| Requirement | How we cover it |
|-------------|-----------------|
| Decision logic | `StateProjectorTest` — forward-only, return, conflict, explanation text |
| Integration / E2E | `ShipmentFlowIntegrationTest` — POST + GET sequence on H2 (Phase 1) |
| Change request | `ChangeRequestIntegrationTest` — **Phase 2 only** — `acme` natural-key resend |

**Hands-on guide for evaluators:** [`docs/WALKTHROUGH.md`](WALKTHROUGH.md) — copy-paste `curl` (or Swagger) steps after `./mvnw spring-boot:run`. Same story as integration tests; use shipment id `ship-demo-001`.

Walkthrough steps map to integrity rules:

| Step | Rule demonstrated |
|------|-------------------|
| 1–3 | Happy path ingest + GET current + GET history order |
| 4 | Duplicate (`eventId`) |
| 5 | Out-of-order (`ACCEPTED_NO_STATE_CHANGE`) |
| 6 | Conflict (`DELIVERY_EXCEPTION` vs `DELIVERED`) |
| 7 | Return after delivery |
| 8 | Invalid status (`REJECTED_INVALID`) |
| 9–10 | `stateExplanation` + chronological audit |
| 11–12 | **Phase 2** — `acme` resend, different `receivedAt` → duplicate |

README will link to `WALKTHROUGH.md` under “Try it”.

---

## 10. Technology (both phases)

| Choice | Reason |
|--------|--------|
| Java 17 + Spring Boot 3 + Maven | Role alignment; standard stack |
| **JPA + Hibernate** + H2 in-memory | Fast local run; ORM maps to entities; **dedupe enforced by DB constraints**, not in-memory maps |
| **Liquibase** | Schema owned in versioned changelogs; Phase 2 = new file (`003`) without ad-hoc SQL scripts |
| springdoc OpenAPI | Clear API contract |
| Structured logging | Debug ingest without full observability stack |
| No Docker | Run with `./mvnw spring-boot:run` only (Maven wrapper included) |

**Stack direction (candidate, before scaffold):** Early AI suggestions leaned toward lighter options (e.g. raw JDBC/SQL scripts, or heavy use of mocks for persistence). I **pre-emptively asked** to use **Hibernate + Liquibase** because they fit this assignment better: real unique constraints for idempotency, a clear schema evolution story for the Phase 2 change request, and integration tests that exercise the same persistence path as production-style Spring services. See [`DEVELOPMENT_PROCESS.md`](DEVELOPMENT_PROCESS.md) §6 (audit trail).

**Alternatives considered (summary):** last-write-wins by `receivedAt`, in-memory dedupe, terminal `DELIVERED` — rejected in ADRs and §7. Full trade-off tables: [`adr/001`](adr/001-forward-only-shipment-status-projection.md), [`adr/002`](adr/002-deduplication-strategy-and-database-constraints.md).

**Package:** `com.accso.shipment` · **Artifact:** `shipment-tracking-service` (single module at repo root)

---

## 11. Deliverables map

| Assignment asks for | Where |
|---------------------|--------|
| Working solution + run instructions | Repo + `README.md` |
| Problem, assumptions, trade-offs, limitations, change request | **`README.md` indexes → this doc** (§1–§3, §6–§7, §10) + ADRs |
| 2 ADRs | `docs/adr/001-*.md`, `002-*.md` |
| Tests (rules + integration + change request) | `src/test/java` (Phase 2 tests added in Commit 2) |
| AI process note + work / audit trail | `docs/DEVELOPMENT_PROCESS.md` |
| ERD + technical design | `docs/design/` |

---

## 12. Implementation order

**Phase 1 (Commit 1)**

1. Scaffold Spring Boot + Liquibase Phase 1 schema  
2. Domain: `StateProjector`, status enum (unit tests)  
3. Ingest with eventId dedupe only  
4. GET endpoints + OpenAPI  
5. Integration tests (Phase 1 scenarios)  
6. README + ADRs + process note  
7. **Commit 1**

**Phase 2 (Commit 2)**

1. Liquibase `003` + partner yaml  
2. Dedupe strategy pattern + natural key  
3. Phase 2 tests  
4. README “change request” section  
5. **Commit 2**

Update [`DEVELOPMENT_PROCESS.md`](DEVELOPMENT_PROCESS.md) (session log, milestones, audit trail) after each step.

---

## 13. Document map

| Doc | Role |
|-----|------|
| **This file** | Problem, assumptions, **Phase 1 vs Phase 2** scope |
| [`docs/design/DATABASE_ERD.md`](design/DATABASE_ERD.md) | Tables — Phase 1 vs Phase 2 schema |
| [`docs/design/CLASS_DIAGRAM.md`](design/CLASS_DIAGRAM.md) | Classes — Phase 1 vs Phase 2 code |
| [`docs/design/README.md`](design/README.md) | How to view Mermaid diagrams |
| [`docs/WALKTHROUGH.md`](WALKTHROUGH.md) | Hands-on curl flow for evaluators |
| [`docs/adr/`](adr/) | Two main design decisions |
| [`docs/DEVELOPMENT_PROCESS.md`](DEVELOPMENT_PROCESS.md) | AI usage, override example, session log, audit trail |

---

*Status: **Phase 1 implemented** (2026-05-19). Design: [`docs/design/`](design/README.md) reconciled to code. **Commit 1** = Phase 1 baseline push; **Commit 2** = change request (Phase 2).*
