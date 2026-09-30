# Observability

Actuator exposes only `health`, `info`, and `prometheus`. Probe endpoints are:

- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/prometheus`

Metrics include `axiom.analysis.runs`, `axiom.analysis.duration`,
`axiom.analysis.stage.duration`, `axiom.analysis.stage.failures`, `axiom.github.requests`,
`axiom.github.errors`, `axiom.github.rate_limits`, `axiom.github.check.publications`,
`axiom.github.pr_comment.publications`, `axiom.webhook.received`, `axiom.webhook.accepted`,
`axiom.webhook.duplicate`, `axiom.webhook.failed`, `axiom.webhook.processing.duration`, and
`axiom.triage.generated`.

Recovery adds `axiom.webhook.recovered`, `axiom.webhook.processing.retries`, and
`axiom.webhook.processing.dead`; recovered counts scheduler submissions/deferred admission, not
exactly-once executions. Historical queries add `axiom.history.query.duration` with fixed operation
tags and `axiom.history.incidents.returned`. Prometheus requires the operator key when security is enabled.

Tags are deliberately low-cardinality: stage, status, operation, outcome, and error category. Commit
SHAs, repositories, fingerprints, test names, webhook delivery IDs, and exception messages are not
metric tags. HTTP correlation IDs and webhook delivery IDs appear in logging context, but payloads,
tokens, secrets, raw CI logs, and analyzed stack traces do not.
