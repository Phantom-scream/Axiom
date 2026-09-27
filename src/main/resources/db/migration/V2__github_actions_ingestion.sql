ALTER TABLE repositories ADD COLUMN default_branch VARCHAR(255);
ALTER TABLE pipeline_runs ADD COLUMN ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE pipeline_runs DROP CONSTRAINT uq_pipeline_runs_repository_external;
ALTER TABLE pipeline_runs ADD CONSTRAINT uq_pipeline_runs_repository_external_attempt UNIQUE (repository_id, external_run_id, attempt);
CREATE INDEX idx_pipeline_runs_external_attempt ON pipeline_runs(external_run_id, attempt);
CREATE INDEX idx_pipeline_jobs_external_job_id ON pipeline_jobs(external_job_id);

CREATE TABLE pipeline_logs (
    id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    storage_type VARCHAR(32) NOT NULL,
    content BYTEA NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pipeline_logs_run UNIQUE (pipeline_run_id)
);
