# Production configuration

Axiom uses typed, validated Spring configuration. The `prod` profile requires
`AXIOM_DB_URL`, `AXIOM_DB_USERNAME`, and `AXIOM_DB_PASSWORD`; local and test profiles do not require
GitHub credentials. Startup fails without a webhook secret when webhook processing is enabled, or
without a token when automatic publishing is enabled. Validation messages never include secret
values.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `AXIOM_DB_URL` | local PostgreSQL URL outside `prod` | JDBC URL |
| `AXIOM_DB_USERNAME` | `axiom` outside `prod` | Database user |
| `AXIOM_DB_PASSWORD` | `axiom` outside `prod` | Database password |
| `AXIOM_GITHUB_TOKEN` | empty | GitHub API token |
| `AXIOM_GITHUB_BASE_URL` | `https://api.github.com` | Fixed provider base URL |
| `AXIOM_GITHUB_WEBHOOK_ENABLED` | `false` | Enable signed webhook receipt |
| `AXIOM_GITHUB_WEBHOOK_SECRET` | empty | HMAC secret; required when enabled |
| `AXIOM_GITHUB_AUTO_PUBLISH_CHECK` | `false` | Publish/update Checks after automation |
| `AXIOM_GITHUB_AUTO_PUBLISH_PR_COMMENT` | `false` | Publish/update PR reports after automation |
| `AXIOM_GITHUB_CONNECT_TIMEOUT` | `5s` | TCP connection timeout |
| `AXIOM_GITHUB_READ_TIMEOUT` | `30s` | HTTP response timeout |
| `AXIOM_GITHUB_REQUEST_TIMEOUT` | `35s` | Overall blocking request bound |
| `AXIOM_GITHUB_RETRY_MAX_ATTEMPTS` | `3` | Total attempts for safe operations |
| `AXIOM_GITHUB_RETRY_INITIAL_BACKOFF` | `200ms` | Initial retry delay |
| `AXIOM_GITHUB_RETRY_MAX_BACKOFF` | `2s` | Retry/Retry-After upper bound |
| `AXIOM_GITHUB_RESPONSE_BYTES` | `25165824` | WebClient in-memory response bound |
| `AXIOM_STORED_LOG_BYTES` | `25165824` | Maximum stored log archive |
| `AXIOM_PUBLISHED_MARKDOWN_CHARACTERS` | `60000` | Check/comment text bound |
| `AXIOM_WEBHOOK_MAX_PAYLOAD_BYTES` | `1048576` | Webhook body bound |
| `AXIOM_WEBHOOK_CORE_THREADS` | `2` | Webhook executor core threads |
| `AXIOM_WEBHOOK_MAX_THREADS` | `4` | Webhook executor maximum threads |
| `AXIOM_WEBHOOK_QUEUE_CAPACITY` | `100` | Bounded pending delivery queue |
| `AXIOM_RERUN_HISTORY_MAX_EXECUTIONS` | `1000` | Most recent executions per stable test (2–10000) |
| `AXIOM_CHANGE_HISTORY_LOOKBACK` | `50` | Prior runs for fingerprint evidence (1–1000) |

Existing analysis settings include `axiom.change-analysis.history-lookback` and the bounded triage
properties documented in the main configuration. See `.env.production.example` for a non-secret
template.

## Final production controls

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `AXIOM_API_SECURITY_ENABLED` | false local / true prod | Static operator authentication |
| `AXIOM_API_KEY` | empty; required when enabled | Operator key, 16–4096 characters (never rendered) |
| `AXIOM_WEBHOOK_RECOVERY_BATCH_SIZE` | 10 | Maximum due work items per scan (1–100) |
| `AXIOM_WEBHOOK_PROCESSING_MAX_ATTEMPTS` | 5 | Durable processing attempts (1–10) |
| `AXIOM_WEBHOOK_PROCESSING_BACKOFF_SECONDS` | 30 | Retry base delay; exponential cap one hour |
| `AXIOM_WEBHOOK_STALE_SECONDS` | 600 | Stale PROCESSING eligibility; live lock remains exclusive |
| `AXIOM_WEBHOOK_RECOVERY_POLL_MILLISECONDS` | 10000 | Recovery poll interval (100–3600000) |
| `AXIOM_HISTORY_MAX_DAYS` | 730 | Date-window ceiling (1–3650) |
| `AXIOM_HISTORY_MAX_RUNS` | 1000 | Run-window ceiling (1–10000) |
| `AXIOM_HISTORY_MAX_RESULTS` | 100 | Top-N ceiling (1–100) |
| `AXIOM_HISTORY_ACTIVE_RECENT_RUNS` | 5 | Recent failed-run rank threshold |
| `AXIOM_HISTORY_RESOLVED_CLEAN_RUNS` | 3 | Observed clean runs after last recurrence |
| `AXIOM_HISTORY_MINIMUM_RECURRING_RUNS` | 2 | Distinct affected runs for recurrence |

Recovery runs only when webhook receipt is enabled. Attempt metadata is durable; no payload or secret
is stored. Compose passes these controls to the application. Ensure the database pool accommodates
dedicated advisory-lock connections; see [operations](operations.md).

Timeouts and retry delays must be between 1ms and two minutes. The API base URL must be HTTPS,
without embedded credentials, query, or fragment; loopback HTTP is accepted for local HTTP fixtures.
