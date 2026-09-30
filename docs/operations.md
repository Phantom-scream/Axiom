# Operations

Monitor readiness, database connectivity, analysis stage failures, GitHub error/rate-limit metrics,
webhook failures, executor saturation, and publication outcomes. Provider operations retry only safe
GET/PATCH requests. Authentication, permission, not-found, and validation errors are never retried;
temporary connection/timeout/502/503/504 and bounded rate-limit responses are retryable.

Webhook records intentionally contain metadata and bounded error summaries, not payloads. A
publication failure does not erase a completed analysis. Recoverable failures retry from the durable
ledger with bounded exponential backoff. Authentication/permission/invalid-event failures terminate;
operators correct configuration and retry explicit operations. Duplicate delivery IDs never create a
second work item.

The executor is an execution mechanism, not the source of truth. Every enabled instance polls a bounded
PostgreSQL recovery window. Session advisory locks span each delivery and workflow attempt; an atomic
claim records PROCESSING and increments attempts. A live lock cannot be stolen merely because the
stale timer expires. Crash/connection loss releases locks, and accepted/stale work becomes recoverable.
Startup recovery runs on the first poll (10 seconds by default). Queue saturation leaves ACCEPTED
work durable and responds 202. Graceful shutdown waits 60 seconds; longer work resumes after staleness.

Use `github_webhook_deliveries` to inspect `attempt_count`, `last_attempted_at`, `next_attempt_at`,
`processing_started_at`, `error_code` and the sanitized summary. After the maximum attempts, no further
automatic retry is scheduled. Monitor recovered/retry/dead counters and investigate dead deliveries.
Legacy V13 rows without attempt identity cannot be reconstructed safely and terminate with
MISSING_WORK_METADATA. Never add raw payloads/credentials when repairing operational data.

Dedicated claim connections require a pool of at least `2 * webhook max threads + 2`; startup validates
this when webhooks are enabled. Direct connections/session affinity are required for advisory locks;
transaction-pooling proxies are unsupported for this claim path. Set `spring.datasource.hikari.maximum-pool-size`
appropriately if increasing executor capacity. See [limitations](limitations.md) for external-write ambiguity.
