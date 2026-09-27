# Git change ingestion

Git change ingestion is an explicit, read-only provider operation. `POST /api/v1/pipeline-runs/{id}/changes/ingest` calls GitHub Compare and persists normalized changes. `GET /api/v1/pipeline-runs/{id}/changes` reads the latest persisted comparison and never calls GitHub.

## Base and head resolution

Pipeline runs persist `commit_sha` as the comparison head plus optional `base_sha` and `event_name`. Pull-request workflow-run metadata supplies PR base/head SHAs for newly ingested runs. Push comparisons are supported only when a trustworthy before/base SHA has been persisted by provider metadata. Axiom never assumes `main`, the repository default branch, or `HEAD~1`; missing metadata returns `GIT_COMPARISON_UNAVAILABLE`.

## GitHub Compare mapping

`GitHubChangeProvider` calls `GET /repos/{owner}/{repo}/compare/{base}...{head}` through the existing authenticated `GitHubApiClient`. GitHub response DTOs remain in the integration layer. File statuses become provider-neutral `ADDED`, `MODIFIED`, `DELETED`, `RENAMED`, `COPIED`, or `UNKNOWN`; renamed files retain `previous_filename` as `previousPath`.

GitHub exposes at most 300 changed files in a Compare response. Axiom bounds processing to that provider contract and does not persist patches, commit bodies, or raw responses.

## Classification and persistence

Every current file path passes through `ChangedFileClassifier`. Categories cover production/test source, dependency manifests and lockfiles, build/CI/infrastructure/database/application configuration, documentation, generated files, and unknown paths. Renames are classified by their current path. The parent directory becomes a bounded module hint when available.

V7 `git_change_sets` identifies a comparison by pipeline run, base, and head. Re-ingestion upserts that row and transactionally replaces its `changed_files`, preventing duplicates. Additions and deletions are reconstructed from normalized file rows on retrieval. V9 adds only the missing pipeline `base_sha` and `event_name` metadata; V1-V8 remain unchanged.

## Authentication and errors

Set `AXIOM_GITHUB_TOKEN` with repository metadata read permission. The API distinguishes authentication, permission, rate-limit, missing comparison, invalid comparison, provider outage, and missing local base metadata failures. Tokens and patch bodies are never logged.

## Current limitations

Already-ingested runs without a base SHA cannot be backfilled safely from the workflow-run record alone. Large comparisons are subject to GitHub's 300-file Compare API limit. Change relevance analysis is outside this ingestion phase.
