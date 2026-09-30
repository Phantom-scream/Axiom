# Testing strategy

Run `./gradlew spotlessCheck`, `./gradlew clean test --rerun-tasks`, `./gradlew build`,
`docker compose config`, and `docker build -t axiom:local .`. No tests are disabled and no live GitHub
credentials are needed. Java 25, Docker, and PostgreSQL 17 Testcontainers are mandatory for full checks.

Unit tests cover extraction/classification/versioned scoring, correlation ambiguity, rerun ordering,
safe rendering, signature checks, configuration and bounded provider retries. HTTP tests use local
mock GitHub servers and actual WebClient serialization/error translation. Controller tests use
Spring MockMvc, not hard-coded endpoint responses.

PostgreSQL integration tests apply the entire Flyway chain to a clean database, then verify evidence,
idempotent derived state, publication identities, and full webhook-to-analysis flows. Recovery tests
simulate accepted/stale database state after restart, competing claims on separate connections,
retry timing/exhaustion, and publication retry without duplicating analysis. Authentication tests
assert unauthorized rejection rather than merely inspecting configuration classes.

Historical tests seed multiple runs/attempts, distinct fingerprints, diagnoses, related files, test
executions and stability snapshots. They check all lifecycle labels, daily/weekly buckets, counting
semantics, top-N/window bounds, empty data, repository isolation, and invalid API inputs. Performance
fixtures include hundreds of runs/signals and thousands of historical executions; assertions check
bounds and deterministic results, not fragile nanosecond benchmarks.

Runtime smoke verification covers production-profile configuration, non-root Docker deployment,
health/readiness, protected Prometheus, valid/duplicate signed webhooks, operator authentication,
and a historical endpoint over local seeded data. Live GitHub checks are separately reported.

Phase 12 verification (2026-09-30): 196 tests, zero failures, zero errors, zero skips. This includes
the original 161-test baseline, clean PostgreSQL migrations V1–V14, transactional retry rollback,
and the final Docker runtime smoke checks. No live GitHub credential was available; provider
coverage used mock HTTP servers, not live GitHub validation.
