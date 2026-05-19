# Development process note — AI tooling

**Assignment:** Accso Technical Interview — Shipment Event Reconciliation Service  
**Time target:** 4–6 hours (record actual time in [§10](#10-time-spent))  
**Design references:** [`ANALYSIS.md`](ANALYSIS.md) · [`adr/001`](adr/001-forward-only-shipment-status-projection.md) · [`adr/002`](adr/002-deduplication-strategy-and-database-constraints.md) · [`WALKTHROUGH.md`](WALKTHROUGH.md)

This document satisfies assignment **deliverable #5**: what AI tools were used, what they produced, what was verified or overridden, and at least one concrete example where human judgment diverged from the AI. It also serves as the merged work log for the project.

**Audit rule:** When correcting or clarifying AI output in chat, add a row to [§6 Clarifications & corrections](#6-clarifications--corrections-audit-trail).

**Submission status (2026-05-19):** All five deliverables complete locally; final commit includes Liquibase `004`, doc reconciliation, and README aligned to the brief. See [§11](#11-assignment-deliverables--where-to-find-them).

---

## Table of contents

| § | Section |
|---|---------|
| [0](#0-assignment-deliverables--where-to-find-them) | Assignment deliverables — where to find them |
| [1](#1-overall-workflow) | Overall workflow |
| [2](#2-ai-tools--what-they-produced) | AI tools & what they produced |
| [3](#3-verification-at-each-step) | Verification at each step |
| [4](#4-concrete-override-example-required) | Concrete override example (required) |
| [5](#5-other-verified--overridden-suggestions) | Other verified / overridden suggestions |
| [6](#6-clarifications--corrections-audit-trail) | Clarifications & corrections (audit trail) |
| [7](#7-session-log) | Session log |
| [8](#8-milestones) | Milestones |
| [9](#9-ai-usage-register) | AI usage register |
| [10](#10-time-spent) | Time spent |
| [11](#11-assignment-deliverables--where-to-find-them) | Assignment deliverables — index |

---

## 0. Assignment deliverables — where to find them

| # | Brief requirement | Satisfied by | Verified how |
|---|-------------------|--------------|--------------|
| 1 | Working solution; clone & run ≤5 min | Root [`README.md`](../README.md) Quick start; `mvnw`, Java 17, H2 | `./mvnw test` + `spring-boot:run`; walkthrough curl |
| 2 | README: framing, assumptions, trade-offs, limitations, change request | [`README.md`](../README.md) sections + [`ANALYSIS.md`](ANALYSIS.md) depth | Candidate review against PDF brief |
| 3 | 2 ADRs (decision, alternatives, why) | [`adr/001`](adr/001-forward-only-shipment-status-projection.md), [`adr/002`](adr/002-deduplication-strategy-and-database-constraints.md) | Each has Decision, Alternatives considered, Consequences |
| 4 | Tests: rules + integration + change request | `StateProjectorTest`, `ShipmentFlowIntegrationTest`, `ChangeRequestIntegrationTest` | 29 tests pass; mirrors `WALKTHROUGH.md` |
| 5 | Development process note (AI + override) | **This file** — §2 tools, §3 verification, §4 override, §6 audit trail | Required §4 example: RETURNED after DELIVERED |

---

## 1. Overall workflow

How the assessment was executed end to end:

```text
Assignment PDF
    │
    ▼
ChatGPT — initial plan of attack (POA)
    │  Fed full assignment; first-cut phases, risks, approach
    ▼
Cursor — ANALYSIS.md
    │  Assignment doc = source of truth
    │  → validate & correct with candidate review
    ▼
Cursor — technical design (ERD + class diagram)
    │  ANALYSIS.md = source of truth
    │  → validate & correct; reconcile design to analysis
    ▼
Cursor — scaffold base repo + implementation
    │  ERD + class diagram = source of truth for structure
    │  → validate & correct against analysis + design
    ▼
┌─ Phase 1 (Commit 1) ──────────────────────────────┐
│  DB setup (Liquibase 001/002)                     │
│  Rest of implementation (APIs, projection, tests) │
│  Validate solution & correct                      │
│  Test: walkthrough curl + integration tests       │
│  Push to git                                      │
└───────────────────────────────────────────────────┘
    ▼
┌─ Phase 2 (Commit 2 — change request) ─────────────┐
│  DB change (Liquibase 003–004; id from 002 IDENTITY) │
│  Rest of implementation (acme, dedupe strategies)│
│  Validate solution & correct                      │
│  Test: walkthrough 11–16 + ChangeRequestIntegrationTest │
│  Push to git (incl. Liquibase 004)                │
└───────────────────────────────────────────────────┘
    ▼
  DONE
```

**Principle:** AI drafts at each step; the **candidate validates** against the current source-of-truth document before moving on. Corrections are recorded in [§6](#6-clarifications--corrections-audit-trail).

---

## 2. AI tools & what they produced

| Step | Tool | Input (SSOT) | What it produced |
|------|------|----------------|------------------|
| Initial POA | **ChatGPT** | Assignment PDF / brief | Informal phase plan, scope sketch, risks — **not** implementation SSOT |
| Analysis | **Cursor** (Auto agent) | Assignment + POA | [`ANALYSIS.md`](ANALYSIS.md) — scope, §7 integrity rules, Phase 1 vs 2, test matrix |
| Lock decisions | **Cursor** + candidate | Candidate policy | Final §4–§10 rules in `ANALYSIS.md` |
| ADRs + process | **Cursor** | Locked analysis | [`adr/001`](adr/001-forward-only-shipment-status-projection.md), [`adr/002`](adr/002-deduplication-strategy-and-database-constraints.md), this document |
| Tech design | **Cursor** | `ANALYSIS.md` + ADRs | [`design/DATABASE_ERD.md`](design/DATABASE_ERD.md), [`design/CLASS_DIAGRAM.md`](design/CLASS_DIAGRAM.md) |
| Doc reconciliation | **Cursor** + candidate | Approved analysis | Phase-split ERD/UML; [`WALKTHROUGH.md`](WALKTHROUGH.md) for developer hands-on scenarios |
| Scaffold | **Cursor** | Design docs | `pom.xml`, Spring Boot app, entities, Liquibase `001`/`002`, smoke test |
| Phase 1 code | **Cursor** | ERD + class diagram + analysis | Full ingest/query stack, `StateProjector`, unit + integration tests |
| Phase 1 refactor | **Cursor** | Code review goals | Mapper, `DomainConfiguration`, clearer ingest paths |
| Phase 2 | **Cursor** + candidate | Analysis §6 + design Phase 2 | `003`/`004` changelogs, `DedupeStrategy`, tests, walkthrough 11–16, doc §14 reconciliation |

**Human control throughout:** stack lock-in (Java 17, Spring Boot, H2, Liquibase), integrity rules, git commit split (Phase 1 vs Phase 2), ADR reinstatement, and every row in the audit trail below.

AI was used for **agentic feature design** (multi-section docs, trade-off tables, scaffolding), not only line completion.

---

## 3. Verification at each step

| Phase | What I verified |
|-------|-----------------|
| Analysis | Rules match assignment PDF; Phase 1 vs 2 scope; deliverables list |
| Design | ERD columns/constraints match Liquibase; class diagram matches packages in `src/main/java` |
| Scaffold | `./mvnw test` passes; Hibernate `validate` matches schema |
| Phase 1 implementation | `./mvnw test` green (26 tests); [`WALKTHROUGH.md`](WALKTHROUGH.md) steps 1–10 at runtime (informal verification); integration tests mirror walkthrough |
| Phase 2 | Walkthrough steps 11–16 + optional verify; `ChangeRequestIntegrationTest` (3 tests); `mvn test` green (29 tests) |

**Runtime check (Phase 1):** After `./mvnw spring-boot:run`, I ran the walkthrough `curl` examples and compared responses to the **Expected** blocks — in addition to automated tests. See [Author notes in `WALKTHROUGH.md`](WALKTHROUGH.md#author-notes).

---

## 4. Concrete override example (required)

### Topic: Is `DELIVERED` a terminal state?

| | Detail |
|---|--------|
| **Context** | While defining status precedence, the AI initially leaned on a model where **`DELIVERED` behaved as terminal** — once delivered, the lifecycle would not admit further meaningful movement except same-timestamp conflicts (e.g. `DELIVERY_EXCEPTION`). |
| **AI suggestion (paraphrased)** | Treat the forward chain as ending at `DELIVERED`; handle conflicts at the same `occurredAt`; avoid “backward” movement when late events arrive. **`RETURNED` was not a first-class post-delivery transition** in early drafts. |
| **My decision** | **Disagree.** **`RETURNED` must be allowed after `DELIVERED`** when `RETURNED.occurredAt` is **after** the latest `DELIVERED.occurredAt`. Real-world logistics requires returns after delivery. |
| **What we built** | Forward-only ordinals for the main chain; **`RETURNED`** only after `latestDeliveredAt`; **`DELIVERY_EXCEPTION`** wins same-timestamp conflicts against `DELIVERED`. Walkthrough step 6 and `StateProjectorTest` prove this. |
| **Why it matters** | Accepting “terminal delivered” would yield **wrong current status** for returned parcels. |
| **Where documented** | `ANALYSIS.md` §4.1; ADR 001; walkthrough step 6; `step06_…` integration test |

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
| No ADRs (analysis only) | **Overridden** — restored 2 ADRs (session 4) |
| `partnerProvidesEventId` on webhook | **Rejected** — server-side yaml partner config (Phase 2) |
| `409` on duplicate | **Rejected** — HTTP **200** idempotent (ANALYSIS §4.2) |
| Skip persisting duplicates | **Rejected** — full-row audit + `payloadMismatch` |
| Actuator / Docker | **Rejected** — time; Maven-only run |
| DedupeStrategy in Phase 1 | **Rejected** — inline dedupe in Phase 1; strategy pattern in Phase 2 |
| Phase 1 + 2 in one commit | **Rejected** — Commit 1 = Phase 1 only; Commit 2 = all Phase 2 |

Full chronology: [§6](#6-clarifications--corrections-audit-trail).

---

## 6. Clarifications & corrections (audit trail)

| When | Topic | AI / prior doc | Candidate correction | Updated artifacts |
|------|--------|----------------|----------------------|-------------------|
| 2026-05-18 | Job profile stack | Generic “pick a stack” | Java 17, Spring Boot, Maven, Liquibase+H2, single module | `ANALYSIS.md` §3 |
| 2026-05-18 | Duplicates | Optional persist | Persist **full row**; signal `duplicate` / `payloadMismatch` on POST | `ANALYSIS.md` §4.2 |
| 2026-05-18 | HTTP on duplicate | 200 vs 409 undecided | **200**; no disposition enum to partners | `ANALYSIS.md` §4.2, §4.5 |
| 2026-05-18 | DELIVERED vs EXCEPTION | Ordinal conflict draft | **DELIVERY_EXCEPTION** wins at same `occurredAt` | `ANALYSIS.md` §4.1 |
| 2026-05-18 | RETURNED after DELIVERED | AI leaned toward **terminal DELIVERED** | Allow **RETURNED** when `occurredAt` after latest **DELIVERED** | `ANALYSIS.md` §4.1, this doc §4, ADR 001 |
| 2026-05-18 | stateExplanation | “When non-obvious” | **Always** include on GET shipment | `ANALYSIS.md` §4.1 |
| 2026-05-18 | Partner eventId flag | Inbound `partnerProvidesEventId` considered | **Server-side yaml config only** | `ANALYSIS.md` §4.6 |
| 2026-05-18 | Change request scope | Slice (hardcoded partner) option | **Full** NaturalKeyDedupeStrategy + partner config | `ANALYSIS.md` §5 |
| 2026-05-18 | ADRs | Omit ADRs (analysis SSOT) | **Add 2 ADRs** on status projection + dedupe | `docs/adr/001`, `002` |
| 2026-05-18 | AI override example (#31) | “Capture during implementation” | Use **RETURNED after DELIVERED** as primary example | This doc §4 |
| 2026-05-18 | Phase 1 vs 2 in one commit | Mixed dedupe strategies in one build | **Commit 1** = Phase 1 only; **Commit 2** = all Phase 2 | `ANALYSIS.md`, ERD, UML |
| 2026-05-18 | DedupeStrategy in Phase 1 | ADR suggested strategy in v1 | **Phase 1** inline eventId dedupe; **Phase 2** strategy pattern | `CLASS_DIAGRAM.md`, ADR 002 |
| 2026-05-19 | stateExplanation generation | AI said “always” but not **how** | `StateProjector.buildExplanation()` templates; persist on `shipment` | `ANALYSIS.md` §7.7, `WALKTHROUGH.md` step 8 |
| 2026-05-19 | History event order | Mentioned in API summary only | **Chronological `occurredAt` ASC**; tie-break `receivedAt`, `id` | `ANALYSIS.md` §7.8, `WALKTHROUGH.md` step 9 |
| 2026-05-19 | Assignment test coverage | Scattered in doc | §9 test matrix + `WALKTHROUGH.md` mirrors scenarios | `ANALYSIS.md` §9 |
| 2026-05-19 | ANALYSIS sign-off | Ongoing edits | **Final review approved** — proceed to implementation | Session 8 |
| 2026-05-19 | ERD/UML post-reconciliation | — | **Candidate verified** design docs | Session 9 |
| 2026-05-19 | ERD/UML vs code | Diagram drift after implementation | Reconciled design docs to **implemented** Phase 1 | `docs/design/*` |
| 2026-05-19 | Persistence stack | AI likely to suggest raw SQL or mock-heavy tests | **Pre-emptively requested Hibernate + Liquibase** for DB constraints and Phase 2 migrations | `ANALYSIS.md` §10, `pom.xml`, Liquibase changelogs |
| 2026-05-19 | `shipment_event.id` in ERD vs Liquibase | Phase 2 ERD shows `bigint id` but **`004` only drops UK** — looked like `id` was missing | **`id` is Phase 1 (`002` `autoIncrement: true`)**; Phase 2 does not alter PK; documented in ERD, `004` comment, `ANALYSIS` §6.1 | `DATABASE_ERD.md`, `002`/`004`, `DEVELOPMENT_PROCESS.md` |
| 2026-05-19 | Phase 2 step 12 `payloadMismatch` | Walkthrough expected `false` for acme retry | **Approach A:** align docs — full JSON hash includes `receivedAt`; expect `payloadMismatch: true` (same as Phase 1 step 3) | `WALKTHROUGH.md`, `ANALYSIS.md` §7.1, ADR 002 §4 |

---

## 7. Session log

| Session | Date | Focus | Tool(s) | Outcome |
|---------|------|--------|---------|---------|
| — | (pre-repo) | Initial POA | **ChatGPT** | Informal plan; input to Cursor analysis |
| 1 | 2026-05-18 | Requirements analysis | Cursor | `ANALYSIS.md` drafted |
| 2 | 2026-05-18 | Stack / role alignment | Cursor | Spring Boot + H2 locked in analysis |
| 3 | 2026-05-18 | Lock design decisions | Cursor | §4–§10 rules finalized |
| 4 | 2026-05-18 | ADRs + process note | Cursor | `adr/001`, `adr/002`, this document started |
| 5 | 2026-05-18 | ERD + UML | Cursor | `docs/design/*` |
| 6 | 2026-05-18 | Phase 1 / 2 doc split | Cursor + candidate | No Phase 2 in Commit 1 diagrams/code |
| 7 | 2026-05-18 | Walkthrough + §7.7–7.8 | Cursor + candidate | `WALKTHROUGH.md`; always explain + history order |
| 8 | 2026-05-19 | Analysis sign-off; design reconcile | Cursor + candidate | **Approved** for implementation |
| 9 | 2026-05-19 | Candidate verified ERD/UML | Candidate | Proceed to scaffold |
| 10 | 2026-05-19 | Scaffold + Liquibase | Cursor | `src/` skeleton, `001`/`002`, `mvn test` PASS |
| 11 | 2026-05-19 | Phase 1 implementation | Cursor | Full APIs, tests mirroring walkthrough |
| 12 | 2026-05-19 | Readability / Spring conventions | Cursor | Mapper, `DomainConfiguration`, ingest refactor |
| — | _TBD_ | Phase 1 git push | — | Commit 1 (baseline) |
| — | _TBD_ | Phase 2 implementation + push | Cursor | Commit 2 |

### Session detail (selected)

**Session 4 — ADRs and override example**  
User reinstated ADRs after an earlier “analysis-only” scope cut. Chose **RETURNED after DELIVERED** as the assignment’s concrete override example (not a generic dedupe example).

**Session 10–12 — Implementation**  
Cursor scaffolded services and tests; candidate verified via `mvn test` and walkthrough runtime checks. Refactored ingest into validate / duplicate / accept paths with `ShipmentPersistenceMapper` and pure-domain `StateProjector` wired in `DomainConfiguration`.

---

## 8. Milestones

### Phase 1 (Commit 1 — baseline)

- [x] Technical design (ERD + UML) reconciled to implementation
- [x] `ANALYSIS.md` — candidate final approval
- [x] Project scaffold + DB (Liquibase `001`, `002`)
- [x] `POST /shipment-events` (eventId dedupe)
- [x] `GET /shipments/{id}` and `GET /shipments/{id}/events`
- [x] `StateProjectorTest` + `ShipmentFlowIntegrationTest` (walkthrough steps 1–10)
- [x] Runtime verification via `WALKTHROUGH.md` (informal)
- [x] 2 ADRs + this development process note
- [x] Git push — Commit 1 (`53cbb22` Initial commit on `main` / baseline)
- [x] Fresh-clone run verified (≤5 min) — `./mvnw test` on clean tree (2026-05-19)

### Phase 2 (Commit 2 — change request)

- [x] Liquibase `003` (nullable `event_id`) + `004` (drop natural-key UK; `id` remains auto-increment from `002`)
- [x] `DedupeStrategy` + partner yaml (`acme` / `dhl`)
- [x] `ChangeRequestIntegrationTest` (walkthrough steps 11–12 + `dhl` missing `eventId`)
- [x] Validate + walkthrough runtime check (steps 11–16, optional, GET history — 2026-05-19)
- [x] All Phase 2 code + docs ready locally (pending final commit/push)
- [ ] **Git push — Commit 2** on `feature/Phase2` (must include `004-natural-key-partial-index.yaml`)

| Metric | Value |
|--------|-------|
| Baseline commit (pre-change-request) | `53cbb22` Initial commit |
| Phase 2 commit (partial, on branch) | `6e5b279` Phase 2 — **superseded by** final commit with `004` + docs |
| Final push target | `feature/Phase2` → remote |

---

## 9. AI usage register

| Date | Tool | Task | Useful? | Override / verification |
|------|------|------|---------|-------------------------|
| (pre-repo) | ChatGPT | Initial POA from assignment | Yes | Informed Cursor analysis only; not SSOT |
| 2026-05-18 | Cursor | Requirements analysis (`ANALYSIS.md`) | Yes | Review §4 rules before coding |
| 2026-05-18 | Cursor | Lock design decisions | Yes | Candidate policy authoritative |
| 2026-05-18 | Cursor | ADRs + development process | Yes | User restored ADRs; RETURNED override §4 |
| 2026-05-18 | Cursor | ERD + UML class diagrams | Yes | Reconciled to analysis; later to code |
| 2026-05-19 | Cursor | Scaffold + Phase 1 implementation | Yes | `mvn test` + walkthrough runtime |
| 2026-05-19 | Cursor + candidate | Phase 2 complete; doc reconciliation §14 | Yes | `mvn test` + walkthrough curl |
| 13 | 2026-05-19 | Assignment deliverables alignment | Cursor + candidate | README + DEVELOPMENT_PROCESS §0/§11; ADR “why” sections | Ready for final git push |

---

## 10. Time spent

| Phase | Approx. time                                 |
|-------|----------------------------------------------|
| ChatGPT POA + Cursor analysis & decisions | 1.5 Hour                                     |
| Tech design (ERD, UML, walkthrough) | 30 Minutes                                   |
| Phase 1 scaffold + implementation + verification | 2 Hours                                      |
| Phase 2 implementation + verification + doc reconciliation | ~2.5 hours |
| **Total (Phase 1 + 2)** | **~6.5 hours** (assignment target 4–6h; documented honestly) |

---

## 11. Assignment deliverables — index

Duplicate of [§0](#0-assignment-deliverables--where-to-find-them) for reviewers landing here first.

**Deliverable 5 (this document) — required elements:**

| Element | Section |
|---------|---------|
| What AI tools were used | [§2](#2-ai-tools--what-they-produced) — ChatGPT (POA), Cursor (analysis, design, code, docs) |
| What AI produced | §2 table (artifacts per step) |
| What I verified / overrode | [§3](#3-verification-at-each-step), [§5](#5-other-verified--overridden-suggestions), [§6](#6-clarifications--corrections-audit-trail) |
| **Concrete override** (judgment ≠ AI) | [§4](#4-concrete-override-example-required) — **RETURNED after DELIVERED** (primary); proactive dedupe + synthetic `event_id` (secondary) |

**Other deliverables:** see [§0](#0-assignment-deliverables--where-to-find-them) and root [`README.md`](../README.md) checklist.

---

*Final update: 2026-05-19 — ready for commit and push of all local changes (including Liquibase `004` and README/deliverable alignment).*
