# Test stability

Stability v1 is evidence based, not predictive. Fewer than five executions yields `INSUFFICIENT_HISTORY`; one unchanged rerun fail/error-to-pass yields `SUSPECTED_FLAKY`; two yields `FLAKY`. Persistent same-fingerprint failures become `CONSISTENTLY_FAILING`, not flaky. A clean twenty-execution history is `STABLE`.

`TestStabilityService` consumes transitions from `RerunAnalysisService`. Only transitions within the same external workflow run and with the same commit SHA count as unchanged-rerun evidence. Failure rate remains descriptive and is never sufficient by itself for a flaky classification.
