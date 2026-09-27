# Test stability

Stability v1 is evidence based, not predictive. Fewer than five executions yields `INSUFFICIENT_HISTORY`; one unchanged rerun fail-to-pass yields `SUSPECTED_FLAKY`; two yields `FLAKY`. Persistent same-fingerprint failures become `CONSISTENTLY_FAILING`, not flaky. A clean twenty-execution history is `STABLE`.
