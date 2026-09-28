CREATE TABLE github_publications (
    id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    publication_type VARCHAR(32) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    external_url TEXT,
    triage_version VARCHAR(32) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uq_github_publication_run_type UNIQUE (pipeline_run_id, publication_type)
);

CREATE INDEX idx_github_publications_pipeline_run
    ON github_publications(pipeline_run_id);
