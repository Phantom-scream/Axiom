# Limitations and completion scope

Axiom is a feature-complete GitHub-native, deterministic CI intelligence backend for its documented
scope. It is not a proof engine, prediction service, identity platform, or universal CI integration.

- Root/primary means investigation priority, not proven root cause. Relevance is not causality.
- Historical labels and first/last seen counts are sampled and bounded, not repository-lifetime truths.
- Test stability snapshots represent the latest persisted analysis, not an immutable temporal ledger.
- GitHub Compare may truncate at the provider's 300-file limitation; historical views cannot recover
  evidence that was never ingested. JUnit reports must be explicitly ingested when available.
- Exactly-once external creation is not guaranteed if GitHub accepts a POST and the connection/process
  fails before its returned ID is persisted. Ambiguous create outcomes stop automatic retries and
  expose GITHUB_PUBLICATION_OUTCOME_UNKNOWN; they require reconciliation. Creates are not blindly HTTP-retried. Normal repeat
  publication uses tracked IDs; operators should reconcile ambiguous external writes before retrying.
- Delivery recovery is at-least-once after a crash, with exclusive PostgreSQL claims and idempotent
  derived state. Stale detection is delayed; database connectivity and appropriate pool sizing are
  necessary. Legacy V13 accepted work lacking an attempt cannot be reconstructed and is marked failed.
- Static operator keys are intentionally minimal. Use TLS, restrict ingress, rotate credentials, back
  up PostgreSQL, and monitor terminal failures. Authorization by user/repository is not provided.
- Automated GitHub coverage uses mock HTTP. Live retrieval/publishing requires repository credentials
  and suitable permissions; do not describe mocks as live validation.

Optional future frontend or AI explanation layers are not backend completion blockers. Any future AI
layer must only explain already-derived evidence, not replace deterministic decisions. Scaling beyond
this deployment scope may warrant additional operator controls and operational automation.
