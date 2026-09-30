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

Authentication of existing management/analysis APIs is not redesigned in this phase. Deploy behind
TLS ingress with operator access restrictions; expose only the signed webhook publicly and keep
Prometheus and explicit publishing endpoints on a protected operational network.

The production image runs as the unprivileged `axiom` user. Run dependency update checks through
Dependabot (`.github/dependabot.yml`) and, where available, the organization-approved dependency
scanner. Gradle dependency insight remains available with `./gradlew dependencies`.
