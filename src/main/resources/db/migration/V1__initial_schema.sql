CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_repositories_provider_owner_name UNIQUE (provider, owner, name)
);

CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY,
    repository_id UUID NOT NULL REFERENCES repositories(id),
    external_run_id BIGINT NOT NULL,
    commit_sha VARCHAR(128) NOT NULL,
    branch VARCHAR(255),
    pull_request_number BIGINT,
    status VARCHAR(32) NOT NULL,
    conclusion VARCHAR(32) NOT NULL,
    attempt INTEGER NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_pipeline_runs_repository_external UNIQUE (repository_id, external_run_id)
);
CREATE INDEX idx_pipeline_runs_external_run_id ON pipeline_runs(external_run_id);
CREATE INDEX idx_pipeline_runs_commit_sha ON pipeline_runs(commit_sha);

CREATE TABLE pipeline_jobs (
    id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    external_job_id BIGINT NOT NULL,
    name VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    conclusion VARCHAR(32) NOT NULL,
    runner_name VARCHAR(255),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT uq_pipeline_jobs_run_external UNIQUE (pipeline_run_id, external_job_id)
);

CREATE TABLE pipeline_steps (
    id UUID PRIMARY KEY,
    pipeline_job_id UUID NOT NULL REFERENCES pipeline_jobs(id) ON DELETE CASCADE,
    step_number INTEGER NOT NULL,
    name VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    conclusion VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT uq_pipeline_steps_job_number UNIQUE (pipeline_job_id, step_number)
);

CREATE TABLE analysis_results (
    id UUID PRIMARY KEY,
    pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id),
    classification VARCHAR(64) NOT NULL,
    confidence NUMERIC(4,3) NOT NULL,
    summary TEXT NOT NULL,
    probable_cause TEXT NOT NULL,
    recommended_action TEXT NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_analysis_results_pipeline_run ON analysis_results(pipeline_run_id);

CREATE TABLE failure_evidence (
    id UUID PRIMARY KEY,
    analysis_result_id UUID NOT NULL REFERENCES analysis_results(id) ON DELETE CASCADE,
    evidence_type VARCHAR(64) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    code VARCHAR(128) NOT NULL,
    description TEXT NOT NULL,
    source VARCHAR(255) NOT NULL,
    weight NUMERIC(4,3) NOT NULL
);
CREATE INDEX idx_failure_evidence_analysis_result ON failure_evidence(analysis_result_id);

