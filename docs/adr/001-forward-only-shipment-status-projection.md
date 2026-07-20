# ADR 001: Forward-only shipment status projection

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-05-18 |
| **Deciders** | Developer (with AI-assisted analysis); see `docs/DEVELOPMENT_PROCESS.md` for override on `RETURNED` |

## Context

Courier partners send shipment events that may arrive **late**, **out of order**, **duplicated**, or **conflicting**. Downstream systems need a single **current status** per `shipmentId` plus an auditable history.

Supported status values: `LABEL_CREATED`, `HANDED_TO_CARRIER`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `DELIVERY_EXCEPTION`, `RETURNED`.

We must choose how to derive **current status** from an unordered stream—not merely “last event received.”

## Decision

We derive current status using a **forward-only lifecycle** with explicit ordinals, applied in **`occurredAt`** order (tie-break: `receivedAt`, then insert id).

### Status ordinals (low → high)

| Ordinal | Status |
|--------:|--------|
| 1 | `LABEL_CREATED` |
| 2 | `HANDED_TO_CARRIER` |
| 3 | `IN_TRANSIT` |
| 4 | `OUT_FOR_DELIVERY` |
| 5 | `DELIVERED` |
| 6 | `RETURNED` |
| 7 | `DELIVERY_EXCEPTION` |

### Rules

1. **Forward-only:** An accepted event updates current status only if `newStatus.ordinal > currentStatus.ordinal`.
2. **Late older events:** Accepted into history with disposition `ACCEPTED_NO_STATE_CHANGE`; current status does **not** regress.
3. **`DELIVERED` → `RETURNED`:** `RETURNED` is allowed only when a prior **`DELIVERED`** exists and `RETURNED.occurredAt` is **after** that delivery’s `occurredAt`; otherwise `ACCEPTED_NO_STATE_CHANGE` (see `DEVELOPMENT_PROCESS.md`—human override of AI’s initial “terminal DELIVERED” bias). **`RETURNED` is then the final business status** for that `shipmentId` (no further progression; re-ship = new shipment — `ANALYSIS.md` §7.3.1).
4. **Same `occurredAt` — `DELIVERED` vs `DELIVERY_EXCEPTION`:** Prefer **`DELIVERY_EXCEPTION`** to surface potential issues for support and alerting.
5. **`GET /shipments/{id}`:** Always return **`stateExplanation`** — built by `StateProjector.buildExplanation()` on each projection update and stored on `shipment` (see `ANALYSIS.md` §7.7).

Implementation: domain `StateProjector` (pure Java, unit-tested without Spring).

### Why we chose this

Support and incident teams need a **stable current status** that reflects real-world progression, not webhook arrival order. Forward-only projection plus explicit RETURNED and exception rules is testable and avoids surprising regressions when old events arrive late.

## Alternatives considered

| Alternative | Why not chosen |
|-------------|----------------|
| **Last-write-wins by `receivedAt`** | Hides late truth; fails when old events arrive after new ones. |
| **Strict finite state machine only** | Hard to extend for `RETURNED` after `DELIVERED` without awkward states. |
| **Recompute winner by max ordinal at each timestamp only** | Allows backward jumps when late low-ordinal events arrive; rejected in favour of forward-only. |
| **`DELIVERED` as terminal (no further progress)** | Simple but wrong for returns; rejected after review (see `DEVELOPMENT_PROCESS.md`). |
| **Always take highest ordinal at same `occurredAt`** | Adopted only for **conflict** cases (e.g. exception vs delivered); not for cross-time ordering. |

## Consequences

### Positive

- Deterministic, testable behaviour for out-of-order feeds.
- Support teams get explicit explanations and exception-preferring conflict handling.
- Real-world return-after-delivery is modelled without breaking forward-only semantics for the main chain.

### Negative

- Ordinals must be maintained when adding statuses.
- `RETURNED` rule requires tracking latest `DELIVERED` timestamp per shipment.
- Consumers must read `stateExplanation` and history dispositions to understand non-obvious states.

## References

- `docs/ANALYSIS.md` §4.1, §4.3
- `docs/DEVELOPMENT_PROCESS.md` — AI override on `DELIVERED` / `RETURNED`
