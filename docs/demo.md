# Axiom demo

All outputs below are **illustrative demo data**, not actual production measurements or claims of
live GitHub validation. Start the protected Compose deployment, verify readiness, then ingest a known
workflow using `scripts/dogfood.sh OWNER REPOSITORY RUN_ID`. Supply the operator key via `AXIOM_API_KEY`.
The script analyzes and retrieves triage, but never publishes implicitly.

## Transient infrastructure

A database connection refusal precedes many integration-test symptoms. A documentation-only change,
prior matching fingerprint, and same-SHA rerun success provide counter-evidence against the change.
Representative triage:

```json
{"primaryClassification":"INFRASTRUCTURE_FAILURE","rerunRecommendation":"RECOMMENDED",
 "summary":"An infrastructure signal appears to be the primary investigation priority.",
 "actions":[{"priority":1,"type":"RERUN_PIPELINE"},{"priority":2,"type":"INSPECT_INFRASTRUCTURE"}]}
```

The historical incident may be INTERMITTENT. This does not prove that every current failure is transient.

## Deterministic dependency change

A dependency-resolution failure and related `pom.xml` change lead to an investigation-first result:
NOT_RECOMMENDED rerun, INSPECT_DEPENDENCY_CONFIGURATION, and no invented remediation command.
Inspect persisted relevance evidence and related files; do not claim that association proves causation.

## Flaky test / unchanged rerun

Ingest a failing JUnit execution and later passing attempt on the same SHA. Correlation connects the
test to the extracted signal, explicit rerun analysis shows FAIL_TO_PASS, and sufficient repeated
evidence supports the existing stability-v1 labels. `/tests/reliability` reports execution/pass/failure
counts and recent rerun recovery without changing the classifier.

## Representative GitHub delivery

An explicitly published neutral Check named **Axiom CI Intelligence**, or marker-bearing PR comment,
can contain:

```text
Axiom CI Intelligence
Primary investigation priority: INFRASTRUCTURE_FAILURE
Change relevance: UNLIKELY_RELATED
Rerun recommendation: RECOMMENDED
Evidence: matching prior failure; unchanged rerun recovered
Actions: rerun once; inspect the affected service if it repeats
```

Use the existing publishing endpoints only with an explicitly safe disposable target. Repeat a
successful publication to confirm tracked update behavior. No raw logs/stack traces belong in reports.

## Historical views

```bash
curl -H "X-Axiom-Api-Key: ${AXIOM_API_KEY}" \
  'http://localhost:8080/api/v1/repositories/<id>/incidents?days=90&limit=20'
curl -H "X-Axiom-Api-Key: ${AXIOM_API_KEY}" \
  'http://localhost:8080/api/v1/repositories/<id>/trends?days=90&granularity=WEEK&maxRuns=500'
```

Explain the returned sample window before presenting counts. Complete the walkthrough with lifecycle,
test reliability, and hotspot endpoints. Empty outputs are valid when evidence is not available.
