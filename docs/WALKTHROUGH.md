# Hands-on walkthrough

Step-by-step guide for **developers** (or reviewers) to run the service locally, send real HTTP requests, and see how shipment ingest, projection, and read APIs behave. Each step is a small scenario you can try in isolation or as a full story on one shipment id.

Run **Phase 1** steps **1–10 in order** on a fresh app instance so later steps build on earlier state (`ship-demo-001`, partner `dhl`).

**Phase 2** (steps **11–16**) uses a **different** shipment (`ship-acme-001`, partner `acme`). You can run it **after** Phase 1 on the same running app, or **alone** after a restart — it does not depend on `ship-demo-001`.

The same flows are automated in `ShipmentFlowIntegrationTest` (steps 1–10) and `ChangeRequestIntegrationTest` (steps 11–12, `dhl` without `eventId`) if you prefer not to use curl.

**Not a formal test plan** — see [Author notes](#author-notes) at the end for how this doc was also used during implementation.

## Before you start

| Item | Value |
|------|--------|
| Start command | `./mvnw spring-boot:run` (from project root; Windows: `.\mvnw.cmd`) |
| Base URL | http://localhost:8080 |
| Demo shipment id | `ship-demo-001` |
| Partner (Phase 1) | `dhl` — **`eventId` required** (event-id dedupe) |
| Partner (Phase 2) | `acme` — **`eventId` optional** (natural-key dedupe) |
| Phase 2 shipment id | `ship-acme-001` |
| Partner config | `src/main/resources/application.yml` → `shipment.partners.*.dedupe-strategy` |
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
| 13 | **Phase 2** — GET shipment + history (audit rows) |
| 14 | **Phase 2** — new natural key → forward status |
| 15 | **Phase 2** — duplicate with body change → `payloadMismatch` |
| 16 | **Phase 2** — `dhl` without `eventId` → 400 |
| Optional + verify | **Phase 2** — out-of-order `acme` + GET history (5 rows) |

---

## Progress checklist (Phase 2)

Run on `ship-acme-001` after Phase 2 implementation. Can follow Phase 1 on the same JVM or restart and run only these steps.

| Step | Topic | Pass? |
|------|--------|-------|
| 11 | `acme` ingest without `eventId` → accepted | ☐ |
| 12 | Same `(status, occurredAt)`, new `receivedAt` → duplicate + `payloadMismatch` | ☐ |
| 13 | GET shipment + events (2 rows, one `DUPLICATE`) | ☐ |
| 14 | New `occurredAt` / status → accepted, status moves forward | ☐ |
| 15 | Duplicate resend with different `location` → `payloadMismatch` | ☐ |
| 16 | `dhl` POST without `eventId` → 400 `MISSING_EVENT_ID` | ☐ |
| Optional | Out-of-order `IN_TRANSIT` after `DELIVERED` | ☐ |
| Verify | GET shipment + GET events (5 rows, `processedEventCount` 3) | ☐ |

**Natural-key rule (acme):** duplicate = same `(partner, shipmentId, status, occurredAt)` — **`receivedAt` is ignored for dedupe**, but still in the raw JSON used for **`payloadMismatch`** (ANALYSIS §6.1, §7.1).

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

## Phase 2 — Change request (`acme` + `dhl` validation)

Partner **`acme`** uses **natural-key** dedupe: `(partner, shipment_id, status, occurred_at)` — no `eventId` on the webhook. Partner **`dhl`** still requires **`eventId`** (unchanged from Phase 1).

Configured in `application.yml`:

```yaml
shipment:
  partners:
    dhl:
      dedupe-strategy: event-id
    acme:
      dedupe-strategy: natural-key
```

See [`ANALYSIS.md`](ANALYSIS.md) §6 and [`adr/002-deduplication-strategy-and-database-constraints.md`](adr/002-deduplication-strategy-and-database-constraints.md).

---

### Step 11 — `acme` first ingest (no `eventId`)

Courier sends a status update with **no stable event id** — only business fields.

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

**Expected (HTTP 200):**

| Field | Expected |
|-------|----------|
| `accepted` | `true` |
| `duplicate` | `false` |
| `stateChanged` | `true` |
| `currentStatus` | `"IN_TRANSIT"` |
| `shipmentId` | `"ship-acme-001"` |
| `eventId` | omitted or `null` (acme did not send one) |

**What this proves:** Natural-key partner ingest works without `eventId`; projection creates/updates `ship-acme-001`.

Automated: `ChangeRequestIntegrationTest.step11_givenAcmePartner_whenInTransitPostedWithoutEventId_thenAccepted`.

---

### Step 12 — `acme` resend (duplicate — same natural key, new `receivedAt`)

Assignment change request: partner **retries the same logical update** with a later `receivedAt` (e.g. webhook redelivery).

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

**Expected (HTTP 200):**

| Field | Expected |
|-------|----------|
| `accepted` | `false` |
| `duplicate` | `true` |
| `stateChanged` | `false` |
| `payloadMismatch` | `true` (only `receivedAt` differs in the JSON — same as Phase 1 step 3) |
| `currentStatus` | `"IN_TRANSIT"` |

**Dedupe vs payload hash:** The **duplicate key** ignores `receivedAt` (`status` + `occurredAt` only). **`payloadMismatch`** compares SHA-256 of the **full raw JSON**, which includes `receivedAt`, so a retry with a new `receivedAt` is still a duplicate but flags `payloadMismatch: true`. That is intentional for this PoC (see [`ANALYSIS.md`](ANALYSIS.md) §7.1).

**What this proves:** Same logical update retried → `duplicate: true` + second audit row (`DUPLICATE`); service-layer dedupe — no DB UK on the natural key.

Automated: `ChangeRequestIntegrationTest.step12_givenAcmeInTransit_whenSameUpdateDifferentReceivedAt_thenDuplicate`.

---

### Step 13 — Verify `acme` shipment and audit history

```bash
curl -s http://localhost:8080/shipments/ship-acme-001
```

**Check:**

| Field | Expected |
|-------|----------|
| `currentStatus` | `IN_TRANSIT` |
| `processedEventCount` | `1` (duplicate row excluded — §7.5) |

```bash
curl -s http://localhost:8080/shipments/ship-acme-001/events
```

**Check:**

| Check | Expected |
|-------|----------|
| Total rows | **2** |
| Row 1 | `IN_TRANSIT`, `occurredAt` `2026-04-01T09:00:00Z`, `receivedAt` `09:00:01Z`, disposition **accepted** (e.g. `ACCEPTED` / `ACCEPTED_STATE_CHANGED`) |
| Row 2 | Same `status` + `occurredAt`, `receivedAt` `14:30:00Z`, disposition **`DUPLICATE`** |
| Order | `occurredAt` ASC → `receivedAt` ASC → `id` ASC |

**Note:** Accepted `acme` rows may show `eventId: null` in history. Duplicate rows use an internal synthetic `event_id` in the DB; the API may expose a logical id derived from the natural key (strip `::dup::…` if present).

---

### Step 14 — `acme` forward status (new natural key — not a duplicate)

Same partner, **different** `status` and `occurredAt` → new logical event, not step 11/12.

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "partner": "acme",
    "shipmentId": "ship-acme-001",
    "status": "DELIVERED",
    "occurredAt": "2026-04-02T15:00:00Z",
    "receivedAt": "2026-04-02T15:00:05Z",
    "location": "Johannesburg"
  }'
```

**Expected (HTTP 200):**

| Field | Expected |
|-------|----------|
| `accepted` | `true` |
| `duplicate` | `false` |
| `stateChanged` | `true` |
| `currentStatus` | `"DELIVERED"` |

```bash
curl -s http://localhost:8080/shipments/ship-acme-001
```

**Check:** `currentStatus` `"DELIVERED"`, `processedEventCount` `2`.

**What this proves:** Natural-key dedupe is **per (status, occurredAt)** — a real lifecycle update is not blocked by earlier `IN_TRANSIT` rows.

---

### Step 15 — `acme` duplicate with changed payload (`payloadMismatch`)

Resend step 14’s logical update (`DELIVERED` at `2026-04-02T15:00:00Z`) but change `location` and `receivedAt`.

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "partner": "acme",
    "shipmentId": "ship-acme-001",
    "status": "DELIVERED",
    "occurredAt": "2026-04-02T15:00:00Z",
    "receivedAt": "2026-04-02T18:00:00Z",
    "location": "Pretoria"
  }'
```

**Expected (HTTP 200):**

| Field | Expected |
|-------|----------|
| `duplicate` | `true` |
| `payloadMismatch` | `true` |
| `stateChanged` | `false` |
| `currentStatus` | `"DELIVERED"` |

```bash
curl -s http://localhost:8080/shipments/ship-acme-001/events
```

**Check:** **4** rows total; latest is `DUPLICATE` with `location` `Pretoria` in the stored audit (canonical accepted payload for projection remains step 14).

**What this proves:** Same natural-key duplicate detection + **payload hash** comparison (same pattern as Phase 1 `dhl` step 3).

---

### Step 16 — `dhl` without `eventId` (validation)

Event-id partners still **require** `eventId`. Use a **new** shipment id so you do not disturb `ship-demo-001`.

```bash
curl -s -w "\nHTTP %{http_code}\n" -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "partner": "dhl",
    "shipmentId": "ship-dhl-no-event",
    "status": "IN_TRANSIT",
    "occurredAt": "2026-04-01T10:00:00Z",
    "receivedAt": "2026-04-01T10:00:01Z"
  }'
```

**Expected:**

- HTTP **400**
- JSON `code`: **`MISSING_EVENT_ID`**
- No shipment row for `ship-dhl-no-event` (reject before ingest)

**What this proves:** Partner config drives validation — `acme` may omit `eventId`; `dhl` may not.

Automated: `ChangeRequestIntegrationTest.givenDhlPartner_whenPostWithoutEventId_then400`.

**Next:** Run the [optional](#phase-2--quick-negative-check-optional) out-of-order `acme` step, then [verify the full audit trail](#phase-2--verify-full-acme-history-after-optional) with GET below.

---

### Phase 2 — quick negative check (optional)

Same `acme` shipment: post **another** `IN_TRANSIT` at a **new** `occurredAt` — should **accept**, not duplicate step 11.

```bash
curl -s -X POST http://localhost:8080/shipment-events \
  -H "Content-Type: application/json" \
  -d '{
    "partner": "acme",
    "shipmentId": "ship-acme-001",
    "status": "IN_TRANSIT",
    "occurredAt": "2026-04-03T08:00:00Z",
    "receivedAt": "2026-04-03T08:00:01Z",
    "location": "Durban"
  }'
```

**Expected:** `accepted: true`, `duplicate: false` (out-of-order relative to `DELIVERED` — may get `stateChanged: false` and `currentStatus` still `DELIVERED`; audit row still stored).

---

### Phase 2 — verify full `acme` history (after optional)

Run after steps **11–16** and the optional Durban POST. Confirms the full Phase 2 story on `ship-acme-001`.

```bash
curl -s http://localhost:8080/shipments/ship-acme-001
```

**Check:**

| Field | Expected |
|-------|----------|
| `currentStatus` | `DELIVERED` |
| `processedEventCount` | `3` (duplicates excluded — §7.5) |
| `location` | `Johannesburg` (canonical from step 14, not Pretoria duplicate) |

```bash
curl -s http://localhost:8080/shipments/ship-acme-001/events
```

**Check:**

| Check | Expected |
|-------|----------|
| Total rows | **5** |
| Row 1 | `IN_TRANSIT` `2026-04-01T09:00:00Z`, `ACCEPTED_STATE_CHANGED`, `eventId` null |
| Row 2 | Same `occurredAt`, `DUPLICATE`, synthetic `eventId` `nk::acme::…` |
| Row 3 | `DELIVERED` `2026-04-02T15:00:00Z`, `ACCEPTED_STATE_CHANGED` |
| Row 4 | Same `occurredAt` as row 3, `DUPLICATE`, `location` `Pretoria` |
| Row 5 | `IN_TRANSIT` `2026-04-03T08:00:00Z`, Durban, `ACCEPTED_NO_STATE_CHANGE`, `stateChanged` false |
| Order | `occurredAt` ASC → `receivedAt` ASC → `id` ASC |

If you skip the optional step, expect **4** rows and `processedEventCount` **2** instead.

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
| 11 | `ChangeRequestIntegrationTest.step11_givenAcmePartner_whenInTransitPostedWithoutEventId_thenAccepted` |
| 12 | `ChangeRequestIntegrationTest.step12_givenAcmeInTransit_whenSameUpdateDifferentReceivedAt_thenDuplicate` |
| 13–15 | Manual / runtime only (history + forward + payload mismatch) |
| 16 | `ChangeRequestIntegrationTest.givenDhlPartner_whenPostWithoutEventId_then400` |
| Projection rules | `StateProjectorTest` (`given_*_when_*_then_*`) |

See [`ANALYSIS.md`](ANALYSIS.md) §9.

---

## Author notes

This walkthrough is written primarily so **other developers** can:

- Start the app and hit the APIs without reading the whole codebase first
- Exercise the main integrity rules (duplicates, out-of-order, conflicts, invalid payloads, reads) with copy-paste `curl` examples
- Compare live JSON to the **Expected** blocks and understand what “correct” looks like

During implementation I **also** used this document as an informal **runtime verification** process: after `./mvnw spring-boot:run`, I ran the curl steps and compared JSON to the **Expected** blocks before relying on tests alone.

| Phase | Runtime steps | Automated tests |
|-------|----------------|-----------------|
| Phase 1 | 1–10 + invalid-only extra | `ShipmentFlowIntegrationTest`, `StateProjectorTest` |
| Phase 2 | 11–16 (+ optional negative) | `ChangeRequestIntegrationTest` (11–12, 16) |

Steps **13–15** are especially useful to confirm **audit history** (`DUPLICATE` rows, `processedEventCount`, `payloadMismatch`) for natural-key dedupe — behaviour that is easy to miss if you only check the POST response.

For rule definitions and design rationale, use [`ANALYSIS.md`](ANALYSIS.md) and the ADRs under `docs/adr/`.
