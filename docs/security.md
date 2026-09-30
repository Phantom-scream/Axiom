# Security

Actions log redirects are limited to three GET redirects to HTTPS (loopback HTTP for tests).
Authorization and Cookie headers are removed on every redirect. ZIP expansion is bounded to
25 MB total and 5000 entries; unsafe archive paths are rejected before analysis.

GitHub webhooks are authenticated with `X-Hub-Signature-256` using HMAC SHA-256 and constant-time
digest comparison. Missing, malformed, or mismatched signatures are rejected before parsing or
persistence. Request bodies, header values, provider responses, stored logs, and published Markdown
are bounded. Webhook payloads are not stored.

API requests use only the configured GitHub base URL; signed log downloads may follow the bounded,
credential-free redirects described above. Tokens and webhook secrets are
never persisted or logged. Published reports remain escaped, deterministic summaries and exclude raw
logs and stack traces. Actuator exposure is restricted to health, info, and Prometheus metrics.

Operator APIs and Prometheus/info endpoints require `X-Axiom-Api-Key` when
`AXIOM_API_SECURITY_ENABLED=true`. Production-profile defaults and the production example enable
this; startup fails without a key. Keys are compared in constant time, never logged/persisted/tagged,
and must be supplied over TLS. Public paths are exactly the webhook, `/api/v1/health`, and Actuator
health/liveness/readiness. Webhook HMAC is independent of operator authentication. Local defaults
disable key protection for development; do not use those defaults on public ingress.

Operator keys must contain 16–4096 characters; use at least 32 cryptographically random bytes for production.

This is service/operator authentication, not per-user or per-repository authorization. Rotate keys
through deployment configuration, restrict operator network access, and disable request/header tracing.

The production image runs as the unprivileged `axiom` user. Run dependency update checks through
Dependabot (`.github/dependabot.yml`) and, where available, the organization-approved dependency
scanner. Gradle dependency insight remains available with `./gradlew dependencies`.
