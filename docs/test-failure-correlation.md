# Test failure correlation

Structured JUnit failures are correlated with Phase 3 failure events within the same pipeline run. Correlation is deterministic derived state stored on `test_case_executions` through `correlated_failure_event_id` and `correlation_strength`.

`EXACT` requires a failed or errored test, an identical non-null failure fingerprint, and compatible job identity when both records identify a job. An exact match must be unique.

`STRONG` requires compatible job context, equivalent normalized failure messages, compatible exception types, and one uniquely highest-scoring candidate. Fully equal normalized messages outrank containment matches. Type and job mismatches are rejected.

Ambiguous matches remain `NONE`; Axiom never chooses by database order. Passed and skipped tests are ignored. Report ingestion attempts correlation after persistence, while the explicit `POST /api/v1/pipeline-runs/{id}/tests/correlate` endpoint supports reports ingested before log processing. Repeated correlation updates the same execution rows and creates no duplicate records.
