CREATE TABLE github_webhook_deliveries (
    id UUID PRIMARY KEY,
    delivery_id VARCHAR(128) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    repository_full_name VARCHAR(512),
    external_run_id BIGINT,
    pipeline_run_id UUID REFERENCES pipeline_runs(id) ON DELETE SET NULL,
    status VARCHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    error_code VARCHAR(128),
    error_message TEXT
);

CREATE INDEX idx_webhook_deliveries_status_received
    ON github_webhook_deliveries(status, received_at);

CREATE INDEX idx_pipeline_runs_repository_finished
    ON pipeline_runs(repository_id, finished_at DESC, id);

CREATE INDEX idx_pipeline_runs_repository_pull_request
    ON pipeline_runs(repository_id, pull_request_number)
    WHERE pull_request_number IS NOT NULL;

CREATE INDEX idx_github_publications_type_run
    ON github_publications(publication_type, pipeline_run_id);
