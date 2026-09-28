# Change relevance

Change relevance v1 is deterministic evidence, not causation. It answers whether stored evidence supports a plausible relationship between one pipeline failure and the current base/head comparison.

## Context construction

`FailureChangeContextService` requires a persisted pipeline run, failure event, and Git change set. A diagnosis, correlated structured test, stable-test execution history, and rerun history are optional. Their absence is represented as missing evidence, not as evidence against relevance. Prior fingerprint lookup is limited by `axiom.change-analysis.history-lookback` (default `50`) and ordered by persisted pipeline chronology.

An EXACT correlated test is used normally. A STRONG correlation may contribute path/name evidence with a lower weight. Uncorrelated tests never contribute. Same-commit `FAIL_TO_PASS` and `ERROR_TO_PASS` transitions come from `RerunAnalysisService`; transitions across different SHAs are not unchanged-rerun evidence.

## Evidence and outcomes

Dependency manifests and lockfiles support dependency failures. CI, infrastructure, application, build, and migration configuration support compatible failure classifications. A correlated failing test can relate a changed test file or name-related production file. These are path/module heuristics, not static call-graph claims.

Documentation-only changes, prior occurrences of the same fingerprint, and unchanged rerun passes are counter-evidence. `RELATED`, `LIKELY_RELATED`, `UNLIKELY_RELATED`, `UNRELATED`, and `INDETERMINATE` preserve the existing v1 thresholds. Insufficient or contradictory evidence yields `INDETERMINATE`. Confidence is bounded heuristic strength; it is not a probability and never proves causation.

## Persistence and API

Run analysis only after Git changes have been ingested:

```text
POST /api/v1/pipeline-runs/{id}/changes/analyze
GET  /api/v1/pipeline-runs/{id}/relevance
GET  /api/v1/pipeline-runs/{id}/relevance/{fingerprint}
```

POST analyzes every persisted failure event. Recomputing `change-relevance-v1` updates the same failure/version result and replaces its derived evidence and related-file links atomically. GET operations read persisted results and never recompute or call GitHub. A missing change set returns a controlled prerequisite conflict; an unknown run or fingerprint returns 404.
