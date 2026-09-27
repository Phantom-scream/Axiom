# Classification model

Failure classifications include product regression, test failure, flaky test, dependency/build/environment/infrastructure/configuration failures, timeout, resource exhaustion, external-service failure, and unknown.

Phase 3 extracts explainable failure-event types and fingerprints before it performs any final root-cause classification. A connection event, assertion event, or command failure is evidence—not yet a diagnosis.

Evidence is a typed, weighted statement with a severity, stable code, description, and source. Diagnoses retain their evidence so an operator can understand why a conclusion was made. Bootstrap examples map PostgreSQL connection refusal to infrastructure failure, unresolved dependencies to dependency failure, and `AssertionError` to test failure.

Confidence is deliberately simple today: a deterministic rule emits `0.85` and no match returns `UNKNOWN` at `0.10`. A future model should aggregate independent evidence, account for historical recurrence and rerun behavior, and retain the evidence trail rather than treating confidence as a black box.
