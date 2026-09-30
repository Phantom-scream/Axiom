# Dogfooding

Start PostgreSQL and Axiom, configure a read token, then run:

```bash
scripts/dogfood.sh Phantom-scream Axiom <workflow-run-id>
```

The script ingests one run, invokes `/analyze`, prints persisted triage, and returns the pipeline run
ID. Retrieve repository health with `GET /api/v1/repositories/{repositoryId}/health`.

Publishing remains a separate, explicit safety step:

```bash
curl -X POST http://localhost:8080/api/v1/pipeline-runs/{id}/publish/github-check
curl -X POST http://localhost:8080/api/v1/pipeline-runs/{id}/publish/pr-comment
```

Use a write-enabled token and a disposable PR before exercising writes. Repeat the calls to verify
that the existing Check/comment is updated. Automated tests never require live credentials.
