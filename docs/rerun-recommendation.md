# Rerun recommendations

Triage-v1 returns `RECOMMENDED`, `CONSIDER`, `NOT_RECOMMENDED`, or `INSUFFICIENT_EVIDENCE`. It never executes a rerun.

## Positive evidence

Same-commit fail-to-pass or error-to-pass transitions are the strongest evidence. Infrastructure, environment, and external-service classifications can add support, especially when persisted change relevance is `UNLIKELY_RELATED` or `UNRELATED`. A fingerprint that predates the current run and a flaky/suspected-flaky stability snapshot with unchanged-rerun evidence also contribute.

## Counter-evidence

Deterministic build or dependency failures discourage reruns. `RELATED` change relevance strengthens that conclusion for build, dependency, configuration, and test failures. `CONSISTENTLY_FAILING` tests and unchanged reruns that repeat the same fingerprint are counter-evidence.

Mixed positive and counter-evidence yields `CONSIDER`. With no meaningful evidence, Axiom returns `INSUFFICIENT_EVIDENCE`. Confidence is the bounded strength of the deterministic rule evidence, not a forecast or probability.

The persisted recommendation and supporting evidence are available at:

```text
GET /api/v1/pipeline-runs/{id}/rerun-recommendation
```
