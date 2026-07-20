# Development process — AI-assisted workflow

**Project:** Shipment Event Reconciliation Service  
**Design references:** [`ANALYSIS.md`](ANALYSIS.md) · [`adr/001`](adr/001-forward-only-shipment-status-projection.md) · [`adr/002`](adr/002-deduplication-strategy-and-database-constraints.md) · [`WALKTHROUGH.md`](WALKTHROUGH.md)

This document explains **how this project was built**: what AI tools were used, what they produced, what was verified or overridden, and a concrete example where human judgment diverged from the AI. It demonstrates a professional approach to AI-assisted development — using AI for speed while keeping human ownership of architecture and business rules.

**Audit rule:** When correcting or clarifying AI output, add a row to [§6 Clarifications & corrections](#6-clarifications--corrections-audit-trail).

---

## Table of contents

| § | Section |
|---|---------|
| [1](#1-overall-workflow) | Overall workflow |
| [2](#2-ai-tools--what-they-produced) | AI tools & what they produced |
| [3](#3-verification-at-each-step) | Verification at each step |
| [4](#4-concrete-override-example) | Concrete override example |
| [5](#5-other-verified--overridden-suggestions) | Other verified / overridden suggestions |
| [6](#6-clarifications--corrections-audit-trail) | Clarifications & corrections (audit trail) |
| [7](#7-session-log) | Session log |
| [8](#8-milestones) | Milestones |
| [9](#9-ai-usage-register) | AI usage register |
| [10](#10-time-spent) | Time spent |

---

## 1. Overall workflow

How the project was executed end to end:

```text
Requirements & problem framing
    │
    ▼
ChatGPT — initial plan of attack (POA)
    │  First-cut phases, risks, approach
    ▼
Cursor — ANALYSIS.md
    │  Requirements = source of truth
    │  → validate & correct with human review
    ▼
Cursor — technical design (ERD + class diagram)
    │  ANALYSIS.md = source of truth
    │  → validate & correct; reconcile design to analysis
    ▼
Cursor — scaffold base repo + implementation
    │  ERD + class diagram = source of truth for structure
    │  → validate & correct against analysis + design
    ▼
┌─ Phase 1 ───────────────────────────────────────────┐
│  DB setup (Liquibase 001/002)                     │
│  Rest of implementation (APIs, projection, tests) │
│  Validate solution & correct                      │
│  Test: walkthrough curl + integration tests       │
└───────────────────────────────────────────────────┘
    ▼
┌─ Phase 2 — multi-partner extension ────────────────┐
│  DB change (Liquibase 003–004)                    │
│  Dedupe strategies + partner config (acme)        │
│  Validate solution & correct                      │
│  Test: walkthrough 11–16 + NaturalKeyDedupeIntegrationTest │
└───────────────────────────────────────────────────┘
    ▼
  DONE
```

**Principle:** AI drafts at each step; **human review** validates against the current source-of-truth document before moving on. Corrections are recorded in [§6](#6-clarifications--corrections-audit-trail).

---

## 2. AI tools & what they produced

| Step | Tool | Input (SSOT) | What it produced |
|------|------|----------------|------------------|
| Initial POA | **ChatGPT** | Problem requirements | Informal phase plan, scope sketch, risks — **not** implementation SSOT |
| Analysis | **Cursor** (Auto agent) | Requirements + POA | [`ANALYSIS.md`](ANALYSIS.md) — scope, §7 integrity rules, Phase 1 vs 2, test matrix |
| Lock decisions | **Cursor** + developer | Design policy | Final §4–§10 rules in `ANALYSIS.md` |
| ADRs + process | **Cursor** | Locked analysis | [`adr/001`](adr/001-forward-only-shipment-status-projection.md), [`adr/002`](adr/002-deduplication-strategy-and-database-constraints.md), this document |
| Tech design | **Cursor** | `ANALYSIS.md` + ADRs | [`design/DATABASE_ERD.md`](design/DATABASE_ERD.md), [`design/CLASS_DIAGRAM.md`](design/CLASS_DIAGRAM.md) |
| Doc reconciliation | **Cursor** + developer | Approved analysis | Phase-split ERD/UML; [`WALKTHROUGH.md`](WALKTHROUGH.md) for hands-on scenarios |
| Scaffold | **Cursor** | Design docs | `pom.xml`, Spring Boot app, entities, Liquibase `001`/`002`, smoke test |
| Phase 1 code | **Cursor** | ERD + class diagram + analysis | Full ingest/query stack, `StateProjector`, unit + integration tests |
| Phase 1 refactor | **Cursor** | Code review goals | Mapper, `DomainConfiguration`, clearer ingest paths |
| Phase 2 | **Cursor** + developer | Analysis §6 + design Phase 2 | `003`/`004` changelogs, `DedupeStrategy`, tests, walkthrough 11–16, doc §14 reconciliation |

**Human control throughout:** stack lock-in (Java 17, Spring Boot, H2, Liquibase), integrity rules, phased delivery (Phase 1 vs Phase 2), ADR reinstatement, and every row in the audit trail below.

AI was used for **agentic feature design** (multi-section docs, trade-off tables, scaffolding), not only line completion.

---

## 3. Verification at each step

| Phase | What was verified |
|-------|-------------------|
| Analysis | Rules match requirements; Phase 1 vs 2 scope; documentation completeness |
| Design | ERD columns/constraints match Liquibase; class diagram matches packages in `src/main/java` |
| Scaffold | `./mvnw test` passes; Hibernate `validate` matches schema |
| Phase 1 implementation | `./mvnw test` green (26 tests); [`WALKTHROUGH.md`](WALKTHROUGH.md) steps 1–10 at runtime; integration tests mirror walkthrough |
| Phase 2 | Walkthrough steps 11–16 + optional verify; `NaturalKeyDedupeIntegrationTest` (3 tests); `mvn test` green (29 tests) |

**Runtime check (Phase 1):** After `./mvnw spring-boot:run`, walkthrough `curl` examples were run and responses compared to **Expected** blocks — in addition to automated tests. See [Author notes in `WALKTHROUGH.md`](WALKTHROUGH.md#author-notes).

---

## 4. Concrete override example

### Topic: Is `DELIVERED` a terminal state?

| | Detail |
|---|--------|
| **Context** | While defining status precedence, the AI initially leaned on a model where **`DELIVERED` behaved as terminal** — once delivered, the lifecycle would not admit further meaningful movement except same-timestamp conflicts (e.g. `DELIVERY_EXCEPTION`). |
| **AI suggestion (paraphrased)** | Treat the forward chain as ending at `DELIVERED`; handle conflicts at the same `occurredAt`; avoid “backward” movement when late events arrive. **`RETURNED` was not a first-class post-delivery transition** in early drafts. |
| **My decision** | **Disagree.** **`RETURNED` must be allowed after `DELIVERED`** when `RETURNED.occurredAt` is **after** the latest `DELIVERED.occurredAt`. Real-world logistics requires returns after delivery. |
| **What we built** | Forward-only ordinals for the main chain; **`RETURNED`** only after `latestDeliveredAt`; **`DELIVERY_EXCEPTION`** wins same-timestamp conflicts against `DELIVERED`. Walkthrough step 6 and `StateProjectorTest` prove this. |
| **Why it matters** | Accepting “terminal delivered” would yield **wrong current status** for returned parcels. |
| **Where documented** | `ANALYSIS.md` §7.3.1; ADR 001; walkthrough step 6; integration test |

### Secondary implementation-time override

| | Detail |
|---|--------|
| **AI suggestion** | Rely on DB unique-key violation on insert to detect duplicates. |
| **My decision** | **Proactive** `existsByPartnerAndEventId` before insert; store duplicate audit rows with synthetic `event_id` (`::dup::` suffix) so every duplicate is auditable while API responses keep the logical `eventId`. |
| **Where documented** | ADR 002; `ShipmentPersistenceMapper`; `DATABASE_ERD.md` |

---

## 5. Other verified / overridden suggestions

| AI suggestion | Outcome |
|---------------|---------|
| In-memory dedupe | **Rejected** — DB unique constraints + Liquibase (ADR 002) |
| No ADRs (analysis only) | **Overridden** — restored 2 ADRs |
| `partnerProvidesEventId` on webhook | **Rejected** — server-side yaml partner config (Phase 2) |
| `409` on duplicate | **Rejected** — HTTP **200** idempotent (ANALYSIS §7.1) |
| Skip persisting duplicates | **Rejected** — full-row audit + `payloadMismatch` |
| Actuator / Docker | **Rejected** — time; Maven-only run |
| DedupeStrategy in Phase 1 | **Rejected** — inline dedupe in Phase 1; strategy pattern in Phase 2 |
| Phase 1 + 2 in one commit | **Rejected** — clean phased delivery |

Full chronology: [§6](#6-clarifications--corrections-audit-trail).

---

## 6. Clarifications & corrections (audit trail)

| When | Topic | AI / prior doc | Developer correction | Updated artifacts |
|------|--------|----------------|----------------------|-------------------|
| 2026-05-18 | Stack | Generic “pick a stack” | Java 17, Spring Boot, Maven, Liquibase+H2, single module | `ANALYSIS.md` §3 |
| 2026-05-18 | Duplicates | Optional persist | Persist **full row**; signal `duplicate` / `payloadMismatch` on POST | `ANALYSIS.md` §7.1 |
| 2026-05-18 | HTTP on duplicate | 200 vs 409 undecided | **200**; no disposition enum to partners | `ANALYSIS.md` §7.1 |
| 2026-05-18 | DELIVERED vs EXCEPTION | Ordinal conflict draft | **DELIVERY_EXCEPTION** wins at same `occurredAt` | `ANALYSIS.md` §7.3 |
| 2026-05-18 | RETURNED after DELIVERED | AI leaned toward **terminal DELIVERED** | Allow **RETURNED** when `occurredAt` after latest **DELIVERED** | `ANALYSIS.md` §7.3.1, this doc §4, ADR 001 |
| 2026-05-18 | stateExplanation | “When non-obvious” | **Always** include on GET shipment | `ANALYSIS.md` §7.7 |
| 2026-05-18 | Partner eventId flag | Inbound `partnerProvidesEventId` considered | **Server-side yaml config only** | `ANALYSIS.md` §6 |
| 2026-05-18 | Phase 2 scope | Slice (hardcoded partner) option | **Full** NaturalKeyDedupeStrategy + partner config | `ANALYSIS.md` §6 |
| 2026-05-18 | ADRs | Omit ADRs (analysis SSOT) | **Add 2 ADRs** on status projection + dedupe | `docs/adr/001`, `002` |
| 2026-05-18 | Phase 1 vs 2 delivery | Mixed dedupe strategies in one build | **Phase 1** core only; **Phase 2** extension | `ANALYSIS.md`, ERD, UML |
| 2026-05-18 | DedupeStrategy in Phase 1 | ADR suggested strategy in v1 | **Phase 1** inline eventId dedupe; **Phase 2** strategy pattern | `CLASS_DIAGRAM.md`, ADR 002 |
| 2026-05-19 | stateExplanation generation | AI said “always” but not **how** | `StateProjector.buildExplanation()` templates; persist on `shipment` | `ANALYSIS.md` §7.7, `WALKTHROUGH.md` step 8 |
| 2026-05-19 | History event order | Mentioned in API summary only | **Chronological `occurredAt` ASC**; tie-break `receivedAt`, `id` | `ANALYSIS.md` §7.8, `WALKTHROUGH.md` step 9 |
| 2026-05-19 | Test coverage | Scattered in doc | §9 test matrix + `WALKTHROUGH.md` mirrors scenarios | `ANALYSIS.md` §9 |
| 2026-05-19 | Persistence stack | AI likely to suggest raw SQL or mock-heavy tests | **Chose Hibernate + Liquibase** for DB constraints and Phase 2 migrations | `ANALYSIS.md` §10, `pom.xml`, Liquibase changelogs |
| 2026-05-19 | Phase 2 step 12 `payloadMismatch` | Walkthrough expected `false` for acme retry | **Approach A:** align docs — full JSON hash includes `receivedAt`; expect `payloadMismatch: true` | `WALKTHROUGH.md`, `ANALYSIS.md` §7.1, ADR 002 §4 |

---

## 7. Session log

| Session | Date | Focus | Tool(s) | Outcome |
|---------|------|--------|---------|---------|
| — | (pre-repo) | Initial POA | **ChatGPT** | Informal plan; input to Cursor analysis |
| 1 | 2026-05-18 | Requirements analysis | Cursor | `ANALYSIS.md` drafted |
| 2 | 2026-05-18 | Stack alignment | Cursor | Spring Boot + H2 locked in analysis |
| 3 | 2026-05-18 | Lock design decisions | Cursor | §7 rules finalized |
| 4 | 2026-05-18 | ADRs + process note | Cursor | `adr/001`, `adr/002`, this document started |
| 5 | 2026-05-18 | ERD + UML | Cursor | `docs/design/*` |
| 6 | 2026-05-18 | Phase 1 / 2 doc split | Cursor + developer | No Phase 2 in Phase 1 diagrams/code |
| 7 | 2026-05-18 | Walkthrough + §7.7–7.8 | Cursor + developer | `WALKTHROUGH.md`; always explain + history order |
| 8 | 2026-05-19 | Analysis sign-off; design reconcile | Cursor + developer | **Approved** for implementation |
| 9 | 2026-05-19 | Design verified | Developer | Proceed to scaffold |
| 10 | 2026-05-19 | Scaffold + Liquibase | Cursor | `src/` skeleton, `001`/`002`, `mvn test` PASS |
| 11 | 2026-05-19 | Phase 1 implementation | Cursor | Full APIs, tests mirroring walkthrough |
| 12 | 2026-05-19 | Readability / Spring conventions | Cursor | Mapper, `DomainConfiguration`, ingest refactor |
| 13 | 2026-05-19 | Phase 2 + doc reconciliation | Cursor + developer | `NaturalKeyDedupeIntegrationTest`, Liquibase `004`, §14 |

---

## 8. Milestones

### Phase 1 — core service

- [x] Technical design (ERD + UML) reconciled to implementation
- [x] `ANALYSIS.md` — final approval
- [x] Project scaffold + DB (Liquibase `001`, `002`)
- [x] `POST /shipment-events` (eventId dedupe)
- [x] `GET /shipments/{id}` and `GET /shipments/{id}/events`
- [x] `StateProjectorTest` + `ShipmentFlowIntegrationTest` (walkthrough steps 1–10)
- [x] Runtime verification via `WALKTHROUGH.md`
- [x] 2 ADRs + this development process note
- [x] Fresh-clone run verified (≤5 min) — `./mvnw test` on clean tree

### Phase 2 — multi-partner extension

- [x] Liquibase `003` (nullable `event_id`) + `004` (drop natural-key UK)
- [x] `DedupeStrategy` + partner yaml (`acme` / `dhl`)
- [x] `NaturalKeyDedupeIntegrationTest` (walkthrough steps 11–12 + `dhl` missing `eventId`)
- [x] Validate + walkthrough runtime check (steps 11–16)
- [x] All Phase 2 code + docs complete

---

## 9. AI usage register

| Date | Tool | Task | Useful? | Override / verification |
|------|------|------|---------|-------------------------|
| (pre-repo) | ChatGPT | Initial POA | Yes | Informed Cursor analysis only; not SSOT |
| 2026-05-18 | Cursor | Requirements analysis (`ANALYSIS.md`) | Yes | Review §7 rules before coding |
| 2026-05-18 | Cursor | Lock design decisions | Yes | Developer policy authoritative |
| 2026-05-18 | Cursor | ADRs + development process | Yes | Restored ADRs; RETURNED override §4 |
| 2026-05-18 | Cursor | ERD + UML class diagrams | Yes | Reconciled to analysis; later to code |
| 2026-05-19 | Cursor | Scaffold + Phase 1 implementation | Yes | `mvn test` + walkthrough runtime |
| 2026-05-19 | Cursor + developer | Phase 2 complete; doc reconciliation §14 | Yes | `mvn test` + walkthrough curl |

---

## 10. Time spent

| Phase | Approx. time |
|-------|--------------|
| ChatGPT POA + Cursor analysis & decisions | 1.5 hours |
| Tech design (ERD, UML, walkthrough) | 30 minutes |
| Phase 1 scaffold + implementation + verification | 2 hours |
| Phase 2 implementation + verification + doc reconciliation | ~2.5 hours |
| **Total (Phase 1 + 2)** | **~6.5 hours** |

---

*This process note demonstrates how AI tools accelerate documentation and scaffolding while keeping architecture, business rules, and verification under human control — a workflow suitable for professional freelance and team delivery.*
