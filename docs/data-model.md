# Data model

The initial schema has provider repositories, their pipeline runs, jobs, and steps, plus analysis results and their evidence. UUIDs identify internal records, while provider IDs are stored separately and indexed.

```mermaid
erDiagram
  repositories ||--o{ pipeline_runs : contains
  pipeline_runs ||--o{ pipeline_jobs : contains
  pipeline_jobs ||--o{ pipeline_steps : contains
  pipeline_runs ||--o{ analysis_results : produces
  analysis_results ||--o{ failure_evidence : explains
```

Flyway owns schema evolution. Hibernate validates rather than creates the schema in the production-style configuration.

