# Axiom — CI Failure Intelligence

Axiom is a foundation for explaining failed CI pipeline runs. It will normalize provider data, extract evidence from logs and metadata, classify likely causes, and later correlate tests, Git changes, historical failures, and infrastructure signals.

## Problem statement

CI failures are expensive to triage because their evidence is fragmented and provider-specific. Axiom keeps provider adapters at the edge and builds an explainable, normalized diagnosis in the core.

## Current scope

Axiom ingests GitHub Actions runs and logs, extracts deterministic failure events, classifies them with explainable rules, and ingests framework-neutral JUnit XML. Structured failed tests are correlated to extracted failure events using conservative EXACT or STRONG evidence. Explicit workflow-attempt transitions support stability-v1 without treating unrelated runs or changed commits as unchanged reruns. GitHub Compare ingestion persists provider-neutral, categorized changed files without storing patches. Change-relevance-v1 combines those stored signals into an explainable result for each failure, and triage-v1 persists ranked failures, rerun guidance, and evidence-backed developer actions. The analysis remains deterministic and does not use AI.

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

The local defaults are `jdbc:postgresql://localhost:5432/axiom`, username `axiom`, and password `axiom`. Override them with `AXIOM_DB_URL`, `AXIOM_DB_USERNAME`, and `AXIOM_DB_PASSWORD`.

Set `AXIOM_GITHUB_TOKEN` before requesting GitHub ingestion. Use a fine-grained token with read access to Actions and repository metadata; private repositories require access to that repository. Explicit GitHub Check publishing additionally requires Checks write permission, while PR reporting requires pull-request Issues write permission. Read-only analysis remains available without write permission, and tokens are never logged.

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

For runs with reliable persisted base/head metadata, ingest and retrieve normalized Git changes:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/<id>/changes/ingest
curl http://localhost:8080/api/v1/pipeline-runs/<id>/changes
```

The POST explicitly calls GitHub; GET reads only PostgreSQL. Pull-request workflow metadata supplies base/head SHAs for future ingestions. Runs without a reliable base SHA return a controlled validation error rather than assuming a branch or parent commit. See [Git change ingestion](docs/git-change-ingestion.md).

After change ingestion, analyze and retrieve relevance without another GitHub call:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/<id>/changes/analyze
curl http://localhost:8080/api/v1/pipeline-runs/<id>/relevance
curl http://localhost:8080/api/v1/pipeline-runs/<id>/relevance/<failure-fingerprint>
```

Change relevance is deterministic evidence rather than proof of causation. Results and their evidence are persisted idempotently under `change-relevance-v1`. Confidence expresses heuristic evidence strength, not a probability. Analysis requires an already-ingested change set; optional diagnosis, structured-test, test-history, and rerun evidence improve the result but are not prerequisites. See [change relevance](docs/change-relevance.md).

Compute triage after failure extraction and, where available, diagnosis, test correlation, and change relevance:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/<id>/triage
curl http://localhost:8080/api/v1/pipeline-runs/<id>/triage
curl http://localhost:8080/api/v1/pipeline-runs/<id>/triage/failures
curl http://localhost:8080/api/v1/pipeline-runs/<id>/triage/actions
curl http://localhost:8080/api/v1/pipeline-runs/<id>/rerun-recommendation
```

POST recomputes and atomically replaces derived triage-v1 state. GET endpoints read persisted state only. A primary failure is the best-supported investigation priority, not a proven root cause. Rerun guidance never executes a workflow. See [pipeline triage](docs/pipeline-triage.md) and [rerun recommendations](docs/rerun-recommendation.md).

Run every applicable persisted analysis stage through one coordinating endpoint:

```bash
curl -X POST 'http://localhost:8080/api/v1/pipeline-runs/<id>/analyze?recompute=false'
```

The response reports `COMPLETED`, `REUSED`, `SKIPPED_NO_DATA`, `SKIPPED_NOT_APPLICABLE`, or `FAILED` for each stage. By default, current versioned results are reused; `recompute=true` invokes the existing idempotent stage services. Missing test reports or Git comparison metadata do not prevent partial-evidence triage, and a failed optional stage is returned explicitly rather than hidden. See [end-to-end analysis](docs/end-to-end-analysis.md).

After triage exists, publish it explicitly as a neutral GitHub Check:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/<id>/publish/github-check
```

The check is named `Axiom CI Intelligence`, targets the run's persisted head SHA, and contains bounded deterministic triage rather than raw logs or stack traces. Repeated publishing updates the tracked Check Run instead of creating unbounded duplicates. Normal analysis never publishes. See [GitHub Checks](docs/github-checks.md).

For a pipeline run associated with a pull request, publish or update one marked Axiom report explicitly:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/<id>/publish/pr-comment
```

The comment starts with `<!-- axiom-ci-intelligence -->`. Axiom reuses the tracked comment for repeated publication and for later runs of the same repository pull request, preventing comment spam. It reads persisted triage only and never invokes analysis implicitly. See [GitHub reporting](docs/github-reporting.md).

Retrieve bounded descriptive repository health from persisted history:

```bash
curl 'http://localhost:8080/api/v1/repositories/<repository-id>/health?days=30&maxRuns=200'
```

The response includes run outcomes, failure classifications, top fingerprints, rerun guidance, change relevance, and separate suspected-flaky, flaky, and consistently-failing test counts. Health requests never call GitHub or recompute analysis. See [pipeline health](docs/pipeline-health.md).

## Project structure

`domain` holds normalized concepts, `application` orchestration, `analysis` evidence/classification extensions, `integrations` provider adapters, `persistence` JPA mappings, and `api` transport concerns.

## Security notes

Secrets are supplied only through environment variables and are never logged. Axiom does not persist Compare API patches, publish raw CI logs, or execute repository content. GitHub Check Markdown is escaped and bounded. The demo endpoint accepts bounded text only.
