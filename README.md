# Shipment tracking service

Spring Boot microservice for the Accso technical assignment — **Phase 1** ingest, projection, and query APIs with H2 + Liquibase.

## For reviewers (Accso)

The assignment asks for a README covering problem framing, assumptions, design trade-offs, known limitations, and what changed for the change request. **Most of that narrative lives in [`docs/ANALYSIS.md`](docs/ANALYSIS.md)** (written before and during implementation). This README is the **entry point**: how to run the service, where to read deeper material, and how docs fit together.

| Assignment README topic | Where to read it |
|-------------------------|------------------|
| **Problem framing** (how I interpreted the brief) | [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §1–§2 |
| **Assumptions** | [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §3 |
| **Design choices & trade-offs** (why this approach vs alternatives) | [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §7, §10 · ADRs [`docs/adr/001`](docs/adr/001-forward-only-shipment-status-projection.md), [`002`](docs/adr/002-deduplication-strategy-and-database-constraints.md) |
| **Known limitations** | [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §3.1 |
| **Change request** (what changed / what stayed the same) | [`docs/ANALYSIS.md`](docs/ANALYSIS.md) §6 (Phase 2) — after Commit 2 |
| **AI / development process** | [`docs/DEVELOPMENT_PROCESS.md`](docs/DEVELOPMENT_PROCESS.md) |
| **Try the APIs hands-on** | [`docs/WALKTHROUGH.md`](docs/WALKTHROUGH.md) |
| **Schema & class design** | [`docs/design/DATABASE_ERD.md`](docs/design/DATABASE_ERD.md), [`CLASS_DIAGRAM.md`](docs/design/CLASS_DIAGRAM.md) |

## Prerequisites

- **Java 17** only — Maven is bundled via the wrapper (`mvnw` / `mvnw.cmd`)

## Run

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Service listens on **http://localhost:8080**.

**Try the APIs:** follow [docs/WALKTHROUGH.md](docs/WALKTHROUGH.md) (Phase 1 steps 1–10, copy-paste `curl`). Automated equivalent: `./mvnw test` (`ShipmentFlowIntegrationTest`).

- OpenAPI JSON: http://localhost:8080/api-docs  
- Swagger UI: http://localhost:8080/swagger-ui.html  

## Database (Phase 1)

- **H2** in-memory: `jdbc:h2:mem:shipmentdb`
- **Liquibase** changelogs: `src/main/resources/db/changelog/`
  - `001-create-shipment.yaml` — `shipment` table  
  - `002-create-shipment-event.yaml` — `shipment_event` + `uk_partner_event_id` + timeline index  

JPA `ddl-auto` is **validate** — schema is owned by Liquibase; entities must match.

## Docs

| Doc | Purpose |
|-----|---------|
| [docs/ANALYSIS.md](docs/ANALYSIS.md) | **Primary narrative** — problem, assumptions, rules, trade-offs, limitations, Phase 2 |
| [docs/adr/001-forward-only-shipment-status-projection.md](docs/adr/001-forward-only-shipment-status-projection.md) | ADR — status projection |
| [docs/adr/002-deduplication-strategy-and-database-constraints.md](docs/adr/002-deduplication-strategy-and-database-constraints.md) | ADR — dedupe & DB constraints |
| [docs/design/DATABASE_ERD.md](docs/design/DATABASE_ERD.md) | ERD |
| [docs/design/CLASS_DIAGRAM.md](docs/design/CLASS_DIAGRAM.md) | UML |
| [docs/WALKTHROUGH.md](docs/WALKTHROUGH.md) | Hands-on API walkthrough (steps 1–10) |
| [docs/DEVELOPMENT_PROCESS.md](docs/DEVELOPMENT_PROCESS.md) | AI tooling, process note, session log |

## Tests

```bash
./mvnw test
```

Coverage report (JaCoCo, includes `ShipmentTrackingApplication.main` via application tests):

```bash
./mvnw test jacoco:report
```

Open `target/site/jacoco/index.html`.

## API (Phase 1)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/shipment-events` | Ingest courier webhook |
| GET | `/shipments/{id}` | Current shipment view + `stateExplanation` |
| GET | `/shipments/{id}/events` | Chronological audit (`occurredAt` → `receivedAt` → `id`) |

## Package layout

| Package | Role |
|---------|------|
| `api` | REST controllers, DTOs, exception handling |
| `application` | Use cases (`*Service`), commands, ingest orchestration |
| `domain` | Status rules, projection (`StateProjector`) — no Spring dependencies |
| `infrastructure` | JPA entities, repositories, mappers, hashing |
| `config` | Spring `@Configuration` (e.g. domain bean wiring) |

Start reading ingest flow at `IngestShipmentEventService` (pipeline in class Javadoc), then `StateProjector` for status rules.
