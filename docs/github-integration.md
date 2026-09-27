# GitHub Actions integration

GitHub ingestion uses `AXIOM_GITHUB_TOKEN` and `AXIOM_GITHUB_BASE_URL` (default `https://api.github.com`). Axiom reads one workflow run, all paginated jobs and nested steps, and the run log archive. Provider DTOs remain inside the GitHub integration package and are mapped to provider-neutral domain records.

401, 403, 404, and provider outages become stable Axiom API errors. Tokens and raw CI output are never logged. Downloaded archives are treated as untrusted bytes, bounded to 50 MB, checksummed with SHA-256, and stored in PostgreSQL behind `LogStorage`; nothing is executed.

Identity is repository/provider/external-run-id/run-attempt. Re-ingesting an attempt updates its state and replaces children/logs without duplication. A rerun is stored as a distinct attempt.
