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
  L --> FE[Failure events]
  TR[JUnit XML reports] --> TE[Test executions]
  FE --> TC[TestFailureCorrelationService]
  TE --> TC
  TE --> RA[RerunAnalysisService]
  RA --> TS[TestStabilityService]
```

`CiProvider` is the seam for CI integrations. Test correlation and rerun analysis operate only on provider-neutral persisted pipeline, failure, and test data. Correlation is durable derived state on each test execution; rerun transitions remain derived from raw executions and pipeline attempt metadata.
