# Technical design

Design docs for the shipment tracking service. **Read Phase 1 and Phase 2 sections separately** — Phase 1 is the core service; Phase 2 is the multi-partner extension.

## Document index

| # | Document | Purpose |
|---|----------|---------|
| 1 | [`../ANALYSIS.md`](../ANALYSIS.md) | Problem, assumptions, **Phase 1 vs Phase 2** scope, git plan |
| 2 | [`../adr/001-forward-only-shipment-status-projection.md`](../adr/001-forward-only-shipment-status-projection.md) | Status rules ADR |
| 3 | [`../adr/002-deduplication-strategy-and-database-constraints.md`](../adr/002-deduplication-strategy-and-database-constraints.md) | Dedupe ADR |
| 4 | [`DATABASE_ERD.md`](DATABASE_ERD.md) | Database ERD — **Phase 1** then **Phase 2** schema |
| 5 | [`CLASS_DIAGRAM.md`](CLASS_DIAGRAM.md) | UML — **Phase 1** then **Phase 2** classes |

Also: [`../WALKTHROUGH.md`](../WALKTHROUGH.md) · [`../DEVELOPMENT_PROCESS.md`](../DEVELOPMENT_PROCESS.md)

---

## Viewing Mermaid diagrams (required)

All ERD and UML diagrams in this folder are written in **[Mermaid](https://mermaid.js.org/)**. Plain text editors will show the source code, not the picture. Use one of these to view them:

| Option | How |
|--------|-----|
| **GitHub** | Open the `.md` file on GitHub — Mermaid renders automatically |
| **VS Code / Cursor** | Install extension **“Markdown Preview Mermaid Support”**, open the file, run **Markdown: Open Preview** (`Ctrl+Shift+V` / `Cmd+Shift+V`) |
| **IntelliJ IDEA** | Built-in Mermaid support in Markdown preview (recent versions) |
| **Online** | Copy the ` ```mermaid ` block to [mermaid.live](https://mermaid.live) |

If diagrams look like code blocks only, your viewer does not support Mermaid — use an option above.

---

## Implementation phases vs design

| Phase | Design docs to follow |
|-------|------------------------|
| **Phase 1** | `DATABASE_ERD.md` § Phase 1, `CLASS_DIAGRAM.md` § Phase 1 |
| **Phase 2** | Same files § Phase 2 |

---

## Quick reference

- **Phase 1 dedupe:** `(partner, event_id)` — `eventId` required  
- **Phase 2 adds:** `(partner, shipment_id, status, occurred_at)` for configured partners (e.g. `acme`)  
- **Phase 2 does not change:** status rules, API shapes, audit model, `state_explanation`, history sort, **`shipment_event.id`** (BIGINT auto-increment from Liquibase `002`)  

**Schema note:** Phase 2 ERD diagrams still show `bigint id PK` for completeness; only `003`/`004` migrate dedupe columns — see ERD § Phase 2.

**Parent doc:** [`../ANALYSIS.md`](../ANALYSIS.md) (approved for implementation).

**Latest reconciliation:** 2026-05-19 — Phase 1 + Phase 2 as implemented. **Dedupe:** partner rules in `DedupeStrategy` (service), not a single DB UK on `shipment_event` — see ANALYSIS §6.4, ERD § Dedupe enforcement, CLASS_DIAGRAM § Dedupe enforcement.

**As-built deltas from initial diagrams:** each design doc has an **§ Implementation reconciliation** section (ANALYSIS §14, ERD, CLASS_DIAGRAM, ADR 002) — lists what changed during coding and how we detected it (tests, walkthrough, Liquibase).
