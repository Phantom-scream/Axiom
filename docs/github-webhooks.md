# GitHub webhooks

Configure a repository or GitHub App webhook for:

```text
POST https://your-axiom.example/api/v1/webhooks/github
```

Select the **Workflow runs** event and configure the same strong random secret in GitHub and
`AXIOM_GITHUB_WEBHOOK_SECRET`. Enable receipt with `AXIOM_GITHUB_WEBHOOK_ENABLED=true`.

Axiom verifies the HMAC SHA-256 signature, uses `X-GitHub-Delivery` as a durable unique identity, and
acknowledges valid deliveries with HTTP 202. Unsupported events and non-completed workflow events are
recorded as `IGNORED`. Repeated IDs return `DUPLICATE` without scheduling analysis again. Raw payloads
and secrets are not stored.

Receipt bounds the body before JSON allocation. Completed events resolve the exact notified
`run_attempt` using GitHub's attempt-specific run/jobs/logs endpoints, so a delayed notification never
silently analyzes a newer attempt. Signature comparison is constant-time.

Completed `workflow_run` events are submitted to a bounded in-process executor. The worker reuses
pipeline ingestion and `PipelineAnalysisOrchestrator`. Status moves through `ACCEPTED`, `PROCESSING`,
and `COMPLETED`, with explicit partial/publication failure states. This single-instance queue is
backed by the durable PostgreSQL ledger. Accepted work and stale processing recover after a restart.
Queue saturation does not discard accepted work. Atomic claims and session advisory locks prevent
concurrent execution across instances, including different delivery IDs for the same workflow attempt.
The RECEIVED-to-ACCEPTED work-identity write is one transaction; only committed ACCEPTED work is scheduled.
Processing records attempt count, timestamps, bounded retry timing, and sanitized errors. Duplicate
IDs reuse one ledger item; recovery can retry that item safely up to its configured limit.

Recoverable publication errors retain completed analysis, retry using persisted run/triage/publication
identities, and do not duplicate derived rows. Permanent permission failures are not automatically
retried. See [operations](operations.md) for recovery, stale detection, and pool requirements.

Automatic Checks and PR comments are independent and disabled by default. Enable them with the two
`AXIOM_GITHUB_AUTO_PUBLISH_*` variables. Checks require Checks write permission; PR comments require
Issues write permission. Analysis remains read-only when both are false.
