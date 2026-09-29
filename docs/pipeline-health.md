# Repository pipeline health

`GET /api/v1/repositories/{repositoryId}/health?days=30&maxRuns=200` returns descriptive aggregates from persisted Axiom history. It does not call GitHub and does not run extraction, diagnosis, relevance, stability, or triage.

## Bounded window

Both bounds always apply:

- `days` defaults to 30 and accepts 1–3650.
- `maxRuns` defaults to 200 and accepts 1–1000.

Runs are selected newest-first using their finished, started, ingested, then created timestamp. `window.analyzedRuns` reports how many persisted runs entered the bounded sample.

## Metric definitions

- `runs.total` counts every selected run.
- `successful` counts `SUCCESS`.
- `failed` counts `FAILURE` and `TIMED_OUT`.
- `cancelled` counts `CANCELLED` separately.
- `successRate` is `successful / (successful + failed)`. Cancelled, skipped, neutral, and unknown outcomes are excluded from this denominator. An empty applicable set returns `0.0`. This is historical description, not a forecast.
- Failure classifications count distinct `(pipeline run, fingerprint, Phase 4 classification)` signals. This avoids multiplying repeated occurrences of one fingerprint inside a run while preserving recurrence across runs.
- Top fingerprints sum extracted occurrence counts inside the selected window and return at most ten entries, ordered by occurrence count then fingerprint.
- Rerun recommendations use the latest persisted triage per selected run.
- Change relevance counts persisted change-relevance-v1 failure results.
- Stability counts distinct selected stable test identities from persisted stability-v1 snapshots. `SUSPECTED_FLAKY`, `FLAKY`, and `CONSISTENTLY_FAILING` remain separate.

Repositories with no matching runs return a valid zero-state response with all enum breakdown keys present and zero-valued.

## Limitations

The endpoint reflects only evidence that has already been ingested and analyzed. Missing reports or skipped analysis stages remain absent rather than being inferred. The service intentionally provides no prediction, causal claim, cache, or unbounded historical scan.
