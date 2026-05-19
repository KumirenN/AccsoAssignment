# Technical design

Design docs for the shipment tracking service. **Read Phase 1 and Phase 2 sections separately** — Commit 1 must not include Phase 2 code.

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

## Git commits vs design

| Commit | Phase | Design docs to follow |
|--------|-------|------------------------|
| **1** | Phase 1 | `DATABASE_ERD.md` § Phase 1, `CLASS_DIAGRAM.md` § Phase 1 |
| **2** | Phase 2 | Same files § Phase 2 (planned changes only) |

---

## Quick reference

- **Phase 1 dedupe:** `(partner, event_id)` — `eventId` required  
- **Phase 2 adds:** `(partner, shipment_id, status, occurred_at)` for configured partners (e.g. `acme`)  
- **Phase 2 does not change:** status rules, API shapes, audit model, `state_explanation`, history sort  

**Parent doc:** [`../ANALYSIS.md`](../ANALYSIS.md) (approved for implementation).

**Latest reconciliation:** 2026-05-19 — `DATABASE_ERD.md` and `CLASS_DIAGRAM.md` updated to match **Phase 1 as implemented** (code is source of truth for Commit 1). Phase 2 sections remain planned deltas only.
