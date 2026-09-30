# Design decisions

- **Modular monolith:** one deployable Java/PostgreSQL service, with provider DTOs at the edge and
  evidence/rules in the domain. No speculative broker, distributed cache, warehouse, or frontend.
- **Deterministic reasoning:** stable fingerprints/test IDs and versioned classification, stability,
  relevance, and triage. Scores are heuristic strengths, not probabilities or causal proofs.
- **Raw evidence as source of truth:** derived results replace children transactionally and idempotently.
  Orchestration coordinates existing stages instead of reproducing business logic.
- **Optional evidence:** missing reports, change metadata, or history does not fabricate negative
  evidence. Partial stages remain visible, and triage may remain insufficient.
- **GitHub delivery:** reports read persisted triage; publishing is explicit or separately opted in.
  External identities support updates. The Axiom Check is not a replacement for the source CI result.
- **Durable automation without messaging:** PostgreSQL stores validated work identity, delivery status,
  attempt count, and retry timing. Session advisory locks span the work unit; atomic claims record
  attempts. Crashed connections release locks and a bounded poll recovers stale work. No transaction
  is kept open across GitHub/analysis calls. Executor saturation cannot erase accepted work.
- **Operator authentication:** one static service key, constant-time comparison, TLS required at ingress.
  Webhooks instead use independent GitHub HMAC. No accounts/session/role product is introduced.
- **Bounded historical SQL:** explicit date/run/result caps, grouped projections, exact fingerprint
  grouping, and descriptive labels. Sample boundaries are exposed rather than implying infinite history.
- **Operational observability:** fixed-cardinality metrics; IDs belong in logs, never metric tags.
