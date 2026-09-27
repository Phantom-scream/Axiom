# Rerun analysis

`RerunAnalysisService` derives transitions from persisted test executions and pipeline-run metadata. Raw executions remain the source of truth; transitions are not materialized.

Executions are grouped by stable test ID and external workflow-run ID, ordered strictly by `runAttempt`, and compared between adjacent attempts. Database insertion order is irrelevant. The result exposes source/destination pipeline IDs, attempts, statuses, fingerprints, and `sameCommitSha`.

Supported same-commit transitions are `FAIL_TO_PASS`, `ERROR_TO_PASS`, `FAIL_TO_FAIL`, `ERROR_TO_ERROR`, `PASS_TO_FAIL`, `PASS_TO_PASS`, and `FAIL_TO_DIFFERENT_FAILURE`. A changed commit produces `UNKNOWN` and cannot count as unchanged-rerun evidence. Different external workflow-run IDs are never joined into a rerun transition.

Use `GET /api/v1/tests/{stableTestId}/reruns` to retrieve transitions. Unknown stable IDs return HTTP 404.
