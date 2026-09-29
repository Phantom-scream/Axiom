# GitHub reporting

Axiom exposes two explicit delivery operations for persisted triage:

```text
POST /api/v1/pipeline-runs/{id}/publish/github-check
POST /api/v1/pipeline-runs/{id}/publish/pr-comment
```

Neither endpoint runs analysis, and `/analyze` never publishes.

## Pull-request comments

PR reporting requires a pipeline run with a persisted `pull_request_number` and an already-computed triage-v1 result. Every report begins with the stable marker:

```html
<!-- axiom-ci-intelligence -->
```

The body contains the primary investigation signal, classification, change relevance when available, rerun recommendation, top evidence, up to three actions, and bounded ranked-failure details. It uses deterministic templates and describes evidence rather than proven causation.

## Create/update idempotency

The first request creates an issue comment through GitHub's pull-request Issues API. V12's `github_publications` table stores it as `PR_COMMENT`. Repeated publication PATCHes the stored external comment ID. If a later pipeline attempt or run belongs to the same repository and PR, Axiom finds the prior tracked publication and updates that comment, avoiding one-comment-per-run spam.

The separate `GITHUB_CHECK` publication type continues to track Check Run delivery. No schema change beyond V12 is required.

## Permissions and failures

- Actions/repository ingestion needs read permission.
- GitHub Checks need Checks write permission.
- PR comments need Issues write permission for the target repository.
- Local analysis and retrieval remain available without either write permission.

Authentication, permission, rate-limit, missing-resource, validation, provider outage, and connection failures use Axiom's existing provider error responses.

## Security and limits

Tokens are neither logged nor persisted. Reports exclude raw logs and raw stack traces. Markdown-sensitive persisted values are escaped, and output is capped at 60,000 characters. GitHub and repository-derived values are treated as untrusted text.
