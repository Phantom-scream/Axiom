# Operations

Monitor readiness, database connectivity, analysis stage failures, GitHub error/rate-limit metrics,
webhook failures, executor saturation, and publication outcomes. Provider operations retry only safe
GET/PATCH requests. Authentication, permission, not-found, and validation errors are never retried;
temporary connection/timeout/502/503/504 and bounded rate-limit responses are retryable.

Webhook records intentionally contain metadata and bounded error summaries, not payloads. A
publication failure does not erase a completed analysis. Operators can retry explicit publication or
redeliver a new GitHub delivery after correcting credentials. A duplicate delivery ID is never
reprocessed.

Known limitation: the bounded executor is local to one application instance. Deployments requiring
durable queued execution across crashes or coordinated multi-instance consumption will need a future
database claim/recovery worker or external queue; this phase deliberately does not introduce messaging
infrastructure.
