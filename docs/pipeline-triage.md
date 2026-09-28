# Pipeline triage

Triage v1 ranks specific infrastructure, dependency, resource, compilation, and configuration signals above generic build/command symptoms. It recommends investigation actions and may recommend a rerun only when deterministic evidence supports intermittent operational behavior. Recommendations are not automatic actions and are not causal proof.

## Prerequisites and evidence

Failure extraction is required for meaningful failed-run triage. Diagnoses, correlated structured tests, stability snapshots, rerun transitions, fingerprint history, and persisted change relevance are optional. Missing optional evidence is never treated as negative evidence. Triage does not call GitHub, ingest changes, or recompute relevance.

## Failure roles and ranking

Failures are ordered by deterministic importance. Specific resource, infrastructure, dependency, external-service, configuration, and build signals outrank generic exit/build-summary markers. A generic symptom following a stronger cascade source is `DOWNSTREAM`. Other supported roles are `PRIMARY`, `CONTRIBUTING`, `SECONDARY`, and `UNKNOWN`.

`PRIMARY` means “investigate this signal first.” It is not a claim of proven root cause. When the top signals do not exceed the configured dominance margin, no primary is fabricated.

## Developer actions

Actions are deterministic templates with an explicit reason. At most three are returned by default. A supported rerun recommendation is normally first, followed by classification-specific inspection such as infrastructure, dependency configuration, resources, configuration, build, or tests. Source inspection appears only when persisted relevance links a related source file. No commands, code edits, reruns, or quarantine operations are executed.

## Persistence and API

`POST /api/v1/pipeline-runs/{id}/triage` computes and atomically upserts `triage-v1`. Rankings, evidence, and actions are replaced on recomputation. Raw pipeline, failure, test, and relevance records remain the source of truth.

Persisted retrieval endpoints are:

```text
GET /api/v1/pipeline-runs/{id}/triage
GET /api/v1/pipeline-runs/{id}/triage/failures
GET /api/v1/pipeline-runs/{id}/triage/actions
GET /api/v1/pipeline-runs/{id}/rerun-recommendation
```

GET never recomputes. Successful runs return `NO_TRIAGE_NEEDED`. Cancelled runs and failed runs without extracted events return controlled insufficient-evidence state without a fabricated primary failure.

Importance and rerun confidence values are heuristic evidence strength, not statistical probabilities.
