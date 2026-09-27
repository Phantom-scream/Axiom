# Axiom — CI Failure Intelligence

Axiom is a foundation for explaining failed CI pipeline runs. It will normalize provider data, extract evidence from logs and metadata, classify likely causes, and later correlate tests, Git changes, historical failures, and infrastructure signals.

## Problem statement

CI failures are expensive to triage because their evidence is fragmented and provider-specific. Axiom keeps provider adapters at the edge and builds an explainable, normalized diagnosis in the core.

## Current scope

This release adds GitHub Actions workflow-run ingestion: authenticated metadata/jobs/steps/log download, provider-neutral mapping, idempotent PostgreSQL persistence, rerun-attempt tracking, and stored-log checksums. It does **not** parse JUnit XML or make AI-driven diagnoses.

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

## Project structure

`domain` holds normalized concepts, `application` orchestration, `analysis` evidence/classification extensions, `integrations` provider adapters, `persistence` JPA mappings, and `api` transport concerns.

## Roadmap

Next: authenticated GitHub Actions ingestion, safe log retrieval, normalized persistence mapping, test-report ingestion, real evidence rules, fingerprints, and historical correlation.

## Security notes

Secrets are supplied only through environment variables and are never logged. This version does not execute downloaded artifacts or expose filesystem access. The demo endpoint accepts bounded text only; external CI access is not enabled.
