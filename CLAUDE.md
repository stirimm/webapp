# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

News aggregation webapp for Maramureș region (Romania) — displays recent news items at stiri.maramures.io. Kotlin/Spring Boot backend with server-side Mustache templates. Read-only database; news is ingested by a separate system.

## Build & Run Commands

```bash
# Build and run tests
./mvnw clean install

# Build with Maven (CI-style)
./mvnw --batch-mode --update-snapshots verify

# Run the application (requires DB env vars: DB_HOST, DB_PASS; optional: DB_PORT, DB_NAME, DB_USER)
./mvnw spring-boot:run

# Run tests only (uses in-memory H2, no external DB needed)
./mvnw test
```

## Architecture

**Stack:** Kotlin 1.9 / Java 21 / Spring Boot 3.4 / PostgreSQL / Mustache templates / Maven

Two-route app (`GET /` chronological, `GET /popular` multi-source sorted by source count). Layered architecture:

- **Controller** (`IndexController`) — maps `NewsCluster` to `RenderedNews` (formats dates in Romanian via PrettyTime, builds source chip HTML, computes `sourceCount`), passes to `index.mustache` with `isRecent`/`isPopular` flags for nav highlighting
- **Clustering** (`NewsClusterService`) — groups duplicate articles (same event, different sources) using character trigram Jaccard similarity + union-find. Threshold > 0.35 on `max(titleSim, descSim * 0.9)`. Picks earliest-published as primary.
- **Service** (`NewsService`) — retrieves top 300 recent news, clusters them, returns `List<NewsCluster>`; cached for 1 minute via Caffeine
- **Persistence** — JPA entity `News` + Spring Data `CrudRepository` with custom query `findTop300ByOrderByPublishDateDesc()`
- **Cache** — Caffeine with 1-entry max, 1-min expiry, stats logged every 30 min by `CacheMonitor`

All source lives under `src/main/kotlin/com/emilburzo/stirimm/stirimmwebapp/`.

**Frontend:** Dark-themed responsive page with nav toggle between "cele mai recente" (chronological) and "cele mai populare" (by source count). Client-side JS uses localStorage to track the last-read news item ID and show a "read until here" marker. All news items have uniform gray left border (no blue multi-source distinction).

**Templates:** `header.mustache`, `css.mustache`, `index.mustache`, `footer.mustache` in `src/main/resources/templates/`.

## Database

- **Production:** PostgreSQL, configured via env vars (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`). HikariCP pool is read-only (max 3 connections).
- **Tests:** H2 in-memory (`src/test/resources/application.properties`).

## Deployment

Docker image built via GitHub Actions on push to master. Multi-arch (amd64/arm64). Distroless runtime image (`gcr.io/distroless/java21-debian12:nonroot`). Deployed to Kubernetes with liveness/readiness probes — see `.ci/deploy.yaml` and `.ci/deploy.sh`. Image tagged with `latest`, run number, and short SHA.

## Investigation & Research Guidelines

When doing data analysis, exploratory investigations, or prototyping approaches:

- **Document findings** in `.claude-memory/` (project-local, persists across sessions — do NOT use `~/.claude/` which is ephemeral)
- Create topic-specific files for detailed findings
- Record: what was tested, what worked/didn't, key thresholds/numbers discovered, and why certain approaches were chosen or rejected
- Include concrete examples from the data (article IDs, similarity scores) so future sessions can verify or build on findings

### Existing memory files
- `.claude-memory/deduplication-findings.md` — data analysis: duplicate patterns, similarity thresholds, pg_trgm benchmarks, recommended approach
- `.claude-memory/clustering-implementation.md` — implementation details: algorithm, bugs fixed, UI/UX design decisions, Playwright screenshot testing setup
