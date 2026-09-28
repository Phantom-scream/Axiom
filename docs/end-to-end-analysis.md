# End-to-end analysis

`POST /api/v1/pipeline-runs/{id}/analyze` coordinates the existing deterministic Axiom services for an already-ingested pipeline run. It does not contain alternate analysis rules and it does not publish externally.

## Stage sequence

The fixed order is:

1. `LOG_PROCESSING`
2. `DIAGNOSIS`
3. `TEST_CORRELATION`
4. `TEST_STABILITY`
5. `CHANGE_INGESTION`
6. `CHANGE_RELEVANCE`
7. `TRIAGE`

Each stage returns one of:

- `COMPLETED`: the existing stage service ran successfully.
- `REUSED`: current persisted derived data was retained because `recompute=false`.
- `SKIPPED_NO_DATA`: optional input such as a test report or change set is absent.
- `SKIPPED_NOT_APPLICABLE`: the stage cannot apply, for example when no reliable comparison base SHA exists.
- `FAILED`: the stage threw an error. The response contains a bounded safe message and error code; later stages still evaluate their own prerequisites.

## Recompute and idempotency

`recompute` defaults to `false`. Existing failure events, current-version diagnoses, correlations, stability snapshots, normalized changes, change-relevance-v1 results, and triage-v1 results are reused when valid. This avoids another GitHub Compare request and unnecessary database writes.

With `recompute=true`, the orchestrator calls each applicable existing service. Those services retain their versioned upsert/replacement semantics. Reprocessing raw logs invalidates dependent diagnosis, correlation, relevance, and triage state before the later stages rebuild it, preventing stale references. Repeated execution does not create duplicate versioned relevance or triage rows.

## Partial evidence

Structured test reports and Git changes are optional. Their stages are skipped explicitly when unavailable. Triage still runs from persisted failures and whatever diagnosis/history/relevance evidence exists. A run with no extracted failures receives the established insufficient-evidence/no-triage behavior instead of a fabricated primary failure.

Git change ingestion runs only when reliable persisted base and head SHAs exist. The orchestrator never invents a base branch. Change relevance runs only when a normalized change set and failure events are present.

## Example

```bash
curl -X POST 'http://localhost:8080/api/v1/pipeline-runs/00000000-0000-0000-0000-000000000001/analyze?recompute=false'
```

```json
{
  "pipelineRunId": "00000000-0000-0000-0000-000000000001",
  "recompute": false,
  "stages": [
    {
      "stage": "LOG_PROCESSING",
      "status": "REUSED",
      "message": "Existing extracted failure events reused.",
      "errorCode": null
    },
    {
      "stage": "TEST_CORRELATION",
      "status": "SKIPPED_NO_DATA",
      "message": "No structured test executions are available.",
      "errorCode": null
    }
  ],
  "triageAvailable": true
}
```

External GitHub publishing is deliberately absent from this operation.
