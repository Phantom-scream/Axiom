# GitHub Checks delivery

Axiom can explicitly publish an already-persisted triage-v1 result to the commit associated with its pipeline run:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/<id>/publish/github-check
```

Publishing never runs implicitly from ingestion, analysis, relevance, or triage. Triage must exist first.

## Check semantics

The Check is named `Axiom CI Intelligence` and uses the pipeline run's persisted head commit SHA. Its output contains the supported primary failure, fingerprint, change relevance when available, rerun guidance, top evidence, up to three actions, and bounded ranked-failure details.

The conclusion is `neutral`. The Check reports Axiom's investigation guidance; it does not replace or override the source workflow conclusion. Insufficient evidence is also neutral, and Axiom never invents a source failure.

## Idempotent updates

V12 stores one `GITHUB_CHECK` publication identity per pipeline run. The first request creates a Check Run; later requests PATCH that same external Check Run and refresh the tracked URL and triage version. This prevents uncontrolled duplicate Checks while allowing a recomputed triage to be republished.

## Permissions and errors

Existing GitHub Actions ingestion needs repository/Actions read access. Check publishing additionally needs Checks write permission on the target repository. Analysis and all local retrieval APIs remain usable without that write permission.

GitHub 401, permission-denied 403, rate-limit 403, 404, validation 422, server failures, and connection failures use Axiom's provider error translation. Rate limiting remains distinguishable from an ordinary permission denial.

## Security

- `AXIOM_GITHUB_TOKEN` is read from configuration and is never stored or logged.
- Raw CI logs and raw test stack traces are not included.
- Markdown-sensitive external values are escaped.
- Summary and detail output are bounded below GitHub Check output limits.
- Published wording describes deterministic evidence and investigation priority, not proven causation.
