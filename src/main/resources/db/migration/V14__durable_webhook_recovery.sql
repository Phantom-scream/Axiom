ALTER TABLE github_webhook_deliveries
    ADD COLUMN run_attempt INTEGER,
    ADD COLUMN has_pull_request BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_attempted_at TIMESTAMPTZ,
    ADD COLUMN processing_started_at TIMESTAMPTZ,
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE github_webhook_deliveries d SET run_attempt=p.attempt
FROM pipeline_runs p WHERE p.id=d.pipeline_run_id;

CREATE INDEX idx_webhook_recovery_due ON github_webhook_deliveries(next_attempt_at,received_at)
WHERE status IN ('ACCEPTED','PROCESSING','FAILED','COMPLETED_WITH_STAGE_FAILURE','COMPLETED_WITH_PUBLICATION_FAILURE');
CREATE INDEX idx_pipeline_repository_chronology ON pipeline_runs
    (repository_id,(coalesce(finished_at,started_at,ingested_at,created_at)) DESC,id);
CREATE INDEX idx_changed_files_change_set ON changed_files(change_set_id);
