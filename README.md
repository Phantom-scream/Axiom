# Axiom — CI Failure Intelligence

Axiom is a foundation for explaining failed CI pipeline runs. It will normalize provider data, extract evidence from logs and metadata, classify likely causes, and later correlate tests, Git changes, historical failures, and infrastructure signals.

## Problem statement

CI failures are expensive to triage because their evidence is fragmented and provider-specific. Axiom keeps provider adapters at the edge and builds an explainable, normalized diagnosis in the core.

## Current scope

Axiom ingests GitHub Actions runs and logs, extracts deterministic failure events, classifies them with explainable rules, and ingests framework-neutral JUnit XML. Structured failed tests are correlated to extracted failure events using conservative EXACT or STRONG evidence. Explicit workflow-attempt transitions support stability-v1 without treating unrelated runs or changed commits as unchanged reruns. The analysis remains deterministic and does not use AI.

## Architecture and stack

It is a modular monolith: domain concepts are provider-neutral; application services orchestrate use cases; integrations adapt external systems; persistence entities stay behind repositories; controllers handle HTTP only. Stack: Java 25, Spring Boot 4.1.1, Gradle, PostgreSQL 17, Flyway, JPA, WebClient, Resilience4j, Jackson, and Testcontainers. See [architecture](docs/architecture.md).

## Requirements

Java 25, Docker Compose, and no system Gradle installation (the wrapper is committed).

## Local setup

Start PostgreSQL:

```bash
docker compose up -d postgres
docker compose ps
```

Run the application with local defaults:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The local defaults are `jdbc:postgresql://localhost:5432/axiom`, username `axiom`, and password `axiom`. Override them with `AXIOM_DB_URL`, `AXIOM_DB_USERNAME`, and `AXIOM_DB_PASSWORD`. `AXIOM_GITHUB_TOKEN` is optional and unused until the GitHub adapter is implemented.

Set `AXIOM_GITHUB_TOKEN` before requesting GitHub ingestion. Use a fine-grained token with read access to Actions and repository metadata; private repositories require access to that repository. Tokens are never logged.

## Tests

```bash
./gradlew test
./gradlew build
docker compose config
```

Tests create their own PostgreSQL 17 container via Testcontainers; they do not need the Compose database.

## API endpoints

`GET /api/v1/health` returns Axiom status and version. Actuator is available at `/actuator/health` and `/actuator/info`.

`POST /api/v1/analysis/demo` is expressly demo-only. Example:

```bash
curl -X POST http://localhost:8080/api/v1/analysis/demo \
  -H 'Content-Type: application/json' \
  -d '{"log":"AssertionError: expected true"}'
```

It deterministically recognizes the bootstrap patterns documented in [classification model](docs/classification-model.md).

Ingest a GitHub Actions run:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/ingest -H 'Content-Type: application/json' \
  -d '{"provider":"GITHUB_ACTIONS","repositoryOwner":"Phantom-scream","repositoryName":"Axiom","externalRunId":123456789}'
curl http://localhost:8080/api/v1/pipeline-runs/<axiom-id>
```

Stored log metadata is available at `/api/v1/pipeline-runs/<axiom-id>/logs`. CI logs can contain secrets, so raw-log access remains development-oriented until authentication/authorization is introduced.

Process and diagnose a run deterministically (no AI): `POST /api/v1/pipeline-runs/<id>/process-logs`, then `POST /api/v1/pipeline-runs/<id>/diagnose`. Diagnoses expose evidence-backed categories and use `UNKNOWN` when evidence is insufficient.

Upload JUnit XML with `POST /api/v1/pipeline-runs/<id>/test-reports?sourceName=results.xml`. Correlation is attempted automatically and can be repeated after log processing with `POST /api/v1/pipeline-runs/<id>/tests/correlate`.

Historical test executions use stable IDs. `GET /api/v1/tests/<stableTestId>/history`, `/fingerprints`, `/reruns`, and `/stability` expose deterministic rerun-aware evidence; failure rate alone never labels a test flaky. Rerun transitions compare adjacent attempts of the same external workflow run and explicitly report whether the commit SHA is unchanged.

Change relevance is deterministic evidence rather than proof of causation. Changed-file categories support future pipeline relevance analysis and distinguish source, tests, dependencies, CI, infrastructure, and documentation.

## Project structure

`domain` holds normalized concepts, `application` orchestration, `analysis` evidence/classification extensions, `integrations` provider adapters, `persistence` JPA mappings, and `api` transport concerns.

## Roadmap

Next: authenticated GitHub Actions ingestion, safe log retrieval, normalized persistence mapping, test-report ingestion, real evidence rules, fingerprints, and historical correlation.

## Security notes

Secrets are supplied only through environment variables and are never logged. This version does not execute downloaded artifacts or expose filesystem access. The demo endpoint accepts bounded text only; external CI access is not enabled.
