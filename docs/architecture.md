# Architecture

Axiom is a modular monolith so the first delivery has one deployable unit without entangling its future domains. Core domain records do not know GitHub API classes. Provider adapters translate external representations into `PipelineRun` and `PipelineJob`; application services invoke analysis; controllers expose only HTTP DTOs.

```mermaid
flowchart LR
  P[CI provider adapter] --> N[Normalized pipeline domain]
  N --> C[AnalysisContext]
  C --> E[Evidence extraction]
  E --> F[Failure classifier]
  F --> D[Diagnosis]
  D --> API[Versioned API]
  N --> DB[(PostgreSQL)]
  API --> I[PipelineIngestionService]
  I --> G[GitHubApiClient]
  G --> M[GitHubRunMapper]
  I --> L[LogStorage]
```

`CiProvider` is the seam for GitHub Actions, GitLab CI, and Jenkins. The present GitHub class intentionally throws an explicit unsupported-operation exception: it is a shell, not simulated integration. Future direction is provider ingestion, durable analysis runs, pluggable fingerprinting, and correlation modules while retaining the same domain contract.
