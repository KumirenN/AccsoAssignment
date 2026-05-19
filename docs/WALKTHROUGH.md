# Hands-on walkthrough

Step-by-step guide for **developers** (or reviewers) to run the service locally, send real HTTP requests, and see how shipment ingest, projection, and read APIs behave. Each step is a small scenario you can try in isolation or as a full story on one shipment id.

Run **Phase 1** steps **in order** on a fresh app instance so later steps build on earlier state. The same flow is automated in `ShipmentFlowIntegrationTest` (`step01_` … `step10_`) if you prefer not to use curl.

**Not a formal test plan** — see [Author notes](#author-notes) at the end for how this doc was also used during implementation.

## Before you start

| Item | Value |
|------|--------|
| Start command | `./mvnw spring-boot:run` (from project root; Windows: `.\mvnw.cmd`) |
| Base URL | http://localhost:8080 |
| Demo shipment id | `ship-demo-001` |
| Partner (Phase 1) | `dhl` — **`eventId` required** |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

**Reset data:** restart the app (H2 is in-memory; data is lost on stop).

**Optional:** install [jq](https://jqlang.org/) for pretty JSON (`| jq`). Without jq, omit `| jq` from commands.

**Windows (PowerShell):** use the same `curl` commands; for POST bodies you can use `Invoke-RestMethod` — curl examples below work in Windows 10+ if curl is available.

Optional — confirm the automated suite still passes:

```bash
./mvnw test
```

---

## Progress checklist (Phase 1)

Optional tracker while you work through the flow on `ship-demo-001`. Tick each step when the response matches **Expected** below.

| Step | Topic | Pass? |
|------|--------|-------|
| 1 | First ingest → IN_TRANSIT | ☐ |
| 2 | Forward → DELIVERED | ☐ |
| 3 | Duplicate same `eventId` → 200, no state change | ☐ |
| 4 | Out-of-order HANDED → still DELIVERED | ☐ |
| 5 | Same-instant EXCEPTION wins | ☐ |
| 6 | RETURNED after delivery | ☐ |
| 7 | Invalid status → 400 + audit row | ☐ |
| 8 | GET shipment + `stateExplanation` | ☐ |
| 9 | GET history chronological, 7 rows | ☐ |
| 10 | Unknown id → 404 | ☐ |

**After step 8 you should see:** `processedEventCount: 5` (duplicates and invalid excluded — ANALYSIS §7.5).

**After step 9 you should see:** `7` events; first row `HANDED_TO_CARRIER` at `2026-03-10T11:00:00Z`.

---

## What each step demonstrates

| Step | Assignment topic |
|------|------------------|
| 1–2 | Ingest + forward status |
| 3 | **Duplicate** events (§7.1) |
| 4 | **Out-of-order** events (§7.2) |
| 5 | **Conflicting** same `occurredAt` (§7.3) |
| 6 | Return after delivery |
| 7 | Invalid payload (§7.4) |
| 8 | **`stateExplanation`** (§7.7) |
| 9–10 | Chronological history (§7.8) + 404 |
| 11–12 | **Phase 2** — `acme` natural-key dedupe |

---

## Phase 1 — Full flow (`dhl`, `ship-demo-001`)

### Step 1 — Create shipment: IN_TRANSIT

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-1",
    "partner": "dhl",
    "shipmentId": "ship-demo-001",
    "status": "IN_TRANSIT",
    "occurredAt": "2026-03-10T12:00:00Z",
    "receivedAt": "2026-03-10T12:00:05Z",
    "location": "Amsterdam"
  }'
```

**Expected (HTTP 200):**

- `accepted`: `true`
- `stateChanged`: `true`
- `currentStatus`: `"IN_TRANSIT"`

---

### Step 2 — Move forward: DELIVERED

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-2",
    "partner": "dhl",
    "shipmentId": "ship-demo-001",
    "status": "DELIVERED",
    "occurredAt": "2026-03-10T18:00:00Z",
    "receivedAt": "2026-03-10T18:00:10Z",
    "location": "Rotterdam"
  }'
```

**Expected:** `currentStatus`: `"DELIVERED"`

---

### Step 3 — Duplicate (same `eventId`)

Same `eventId` as step 2, different `receivedAt` (triggers `payloadMismatch: true`).

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-2",
    "partner": "dhl",
    "shipmentId": "ship-demo-001",
    "status": "DELIVERED",
    "occurredAt": "2026-03-10T18:00:00Z",
    "receivedAt": "2026-03-10T19:00:00Z",
    "location": "Rotterdam"
  }'
```

**Expected (HTTP 200):**

- `duplicate`: `true`
- `stateChanged`: `false`
- `payloadMismatch`: `true`
- `currentStatus`: still `"DELIVERED"`

---

### Step 4 — Out-of-order (older `occurredAt`, lower status)

Late webhook: HANDED_TO_CARRIER at 11:00 while current state is already DELIVERED.

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-3",
    "partner": "dhl",
    "shipmentId": "ship-demo-001",
    "status": "HANDED_TO_CARRIER",
    "occurredAt": "2026-03-10T11:00:00Z",
    "receivedAt": "2026-03-10T20:00:00Z",
    "location": "Amsterdam"
  }'
```

**Expected:**

- `accepted`: `true`
- `stateChanged`: `false`
- `currentStatus`: `"DELIVERED"`

**History (step 9):** this row has `disposition`: `ACCEPTED_NO_STATE_CHANGE`

---

### Step 5 — Conflict at same `occurredAt` (EXCEPTION wins)

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-4",
    "partner": "dhl",
    "shipmentId": "ship-demo-001",
    "status": "DELIVERY_EXCEPTION",
    "occurredAt": "2026-03-10T18:00:00Z",
    "receivedAt": "2026-03-10T18:00:15Z",
    "location": "Rotterdam"
  }'
```

**Expected:** `currentStatus`: `"DELIVERY_EXCEPTION"`

---

### Step 6 — RETURNED after delivery

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-5",
    "partner": "dhl",
    "shipmentId": "ship-demo-001",
    "status": "RETURNED",
    "occurredAt": "2026-03-12T10:00:00Z",
    "receivedAt": "2026-03-12T10:00:05Z",
    "location": "Rotterdam"
  }'
```

**Expected:** `currentStatus`: `"RETURNED"`

---

### Step 7 — Invalid status (audit + 400)

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-bad",
    "partner": "dhl",
    "shipmentId": "ship-demo-001",
    "status": "NOT_A_REAL_STATUS",
    "occurredAt": "2026-03-12T11:00:00Z",
    "receivedAt": "2026-03-12T11:00:05Z"
  }'
```

**Expected:**

- HTTP **400**
- JSON body with `code`: `INVALID_STATUS`
- Shipment status **unchanged** (still RETURNED from step 6)

Confirm audit row:

```bash
curl -s http://localhost:8080/shipments/ship-demo-001/events
```

Look for `eventId`: `evt-bad`, `disposition`: `REJECTED_INVALID`

---

### Step 8 — GET current shipment (`stateExplanation`)

```bash
curl -s http://localhost:8080/shipments/ship-demo-001
```

**Check:**

| Field | Expected |
|-------|----------|
| `currentStatus` | `RETURNED` |
| `statusOccurredAt` | `2026-03-12T10:00:00Z` |
| `processedEventCount` | `5` |
| `stateExplanation` | Non-empty sentence (§7.7) |

`processedEventCount` counts accepted dispositions only — not DUPLICATE or REJECTED_INVALID (§7.5).

---

### Step 9 — GET event history (chronological audit)

```bash
curl -s http://localhost:8080/shipments/ship-demo-001/events
```

**Check:**

| Check | Expected |
|-------|----------|
| Total rows | **7** |
| First row (by `occurredAt`) | `HANDED_TO_CARRIER` at `2026-03-10T11:00:00Z` |
| Dispositions present | Includes `DUPLICATE` and `REJECTED_INVALID` |
| Order | `occurredAt` ASC → `receivedAt` ASC → `id` ASC (§7.8) |

**Row guide (by story time):** 11:00 HANDED → 12:00 IN_TRANSIT → 18:00 DELIVERED/EXCEPTION → 12th RETURNED, plus duplicate + invalid audit rows.

---

### Step 10 — Unknown shipment

```bash
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/shipments/ship-does-not-exist
```

**Expected:** HTTP **404**, JSON error (`SHIPMENT_NOT_FOUND`)

```bash
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/shipments/ship-does-not-exist/events
```

**Expected:** HTTP **404**

---

## Extra scenario — invalid-only shipment

Use a **new** shipment id (do not reuse `ship-demo-001` mid-flow).

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-bad-ship-only",
    "partner": "dhl",
    "shipmentId": "ship-bad-001",
    "status": "NOT_A_STATUS",
    "occurredAt": "2026-03-10T12:00:00Z",
    "receivedAt": "2026-03-10T12:00:05Z"
  }'
```

| Request | Expected |
|---------|----------|
| POST | HTTP **400** |
| `GET /shipments/ship-bad-001/events` | HTTP **200**, one `REJECTED_INVALID` row |
| `GET /shipments/ship-bad-001` | HTTP **404** (no projection row — §7.4) |

Automated: `givenOnlyInvalidIngest_whenGetEvents_then200ButGetShipment404` in `ShipmentFlowIntegrationTest`.

---

## Phase 2 — Change request (`acme`)

Partner `acme` uses natural-key dedupe `(partner, shipment_id, status, occurred_at)` — no `eventId` required. Configured in `application.yml` (see `docs/ANALYSIS.md` §6).

### Step 11 — First ingest (no `eventId`)

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "partner": "acme",
    "shipmentId": "ship-acme-001",
    "status": "IN_TRANSIT",
    "occurredAt": "2026-04-01T09:00:00Z",
    "receivedAt": "2026-04-01T09:00:01Z",
    "location": "Cape Town"
  }'
```

**Expected:** `accepted: true` (after Phase 2 implementation)

---

### Step 12 — Resend same update, different `receivedAt` (duplicate)

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "partner": "acme",
    "shipmentId": "ship-acme-001",
    "status": "IN_TRANSIT",
    "occurredAt": "2026-04-01T09:00:00Z",
    "receivedAt": "2026-04-01T14:30:00Z",
    "location": "Cape Town"
  }'
```

**Expected:** `duplicate: true`, `stateChanged: false`  
Automated: `ChangeRequestIntegrationTest` (`step11_…`, `step12_…`).

---

## Map to automated tests

| Walkthrough step | Test method (`ShipmentFlowIntegrationTest`) |
|------------------|---------------------------------------------|
| 1 | `step01_givenNewShipment_whenInTransitPosted_thenAcceptedWithInTransit` |
| 2 | `step02_givenInTransit_whenDeliveredPosted_thenCurrentStatusDelivered` |
| 3 | `step03_givenDelivered_whenSameEventIdRepPosted_thenDuplicateWithoutStateChange` |
| 4 | `step04_givenDelivered_whenOlderHandedToCarrierPosted_thenNoStateChange` |
| 5 | `step05_givenDeliveredAtInstant_whenExceptionAtSameInstant_thenExceptionWins` |
| 6 | `step06_givenException_whenReturnedAfterDelivery_thenCurrentStatusReturned` |
| 7 | `step07_givenShipment_whenInvalidStatusPosted_then400AndAudited` |
| 8 | `step08_givenReturnedShipment_whenGetCurrent_thenStateExplanationPresent` |
| 9 | `step09_givenDemoHistory_whenGetEvents_thenChronologicalWithAllDispositions` |
| 10 | `step10_givenUnknownId_whenGetShipment_then404` / `step10b_..._whenGetEvents_then404` |
| Invalid-only extra | `givenOnlyInvalidIngest_whenGetEvents_then200ButGetShipment404` |
| 11 | `step11_givenAcmePartner_whenInTransitPostedWithoutEventId_thenAccepted` |
| 12 | `step12_givenAcmeInTransit_whenSameUpdateDifferentReceivedAt_thenDuplicate` |
| dhl without eventId | `givenDhlPartner_whenPostWithoutEventId_then400` |
| Projection rules | `StateProjectorTest` (`given_*_when_*_then_*`) |

See [`ANALYSIS.md`](ANALYSIS.md) §9.

---

## Author notes

This walkthrough is written primarily so **other developers** can:

- Start the app and hit the APIs without reading the whole codebase first
- Exercise the main integrity rules (duplicates, out-of-order, conflicts, invalid payloads, reads) with copy-paste `curl` examples
- Compare live JSON to the **Expected** blocks and understand what “correct” looks like

During Phase 1 implementation I **also** used this document as an informal **runtime verification** process: after `./mvnw spring-boot:run`, I ran steps 1–10 (and the invalid-only extra scenario) against a local instance and checked responses matched the expectations here before relying on the integration tests alone. That personal QA pass is not the document’s main purpose — it is a side effect of having executable examples — but it gave confidence that behaviour matched [`ANALYSIS.md`](ANALYSIS.md) at the HTTP boundary.

For automated regression, use `./mvnw test` (`ShipmentFlowIntegrationTest`, `StateProjectorTest`). For rule definitions and design rationale, use `ANALYSIS.md` and the ADRs under `docs/adr/`.
