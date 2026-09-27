CREATE TABLE failure_events (
 id UUID PRIMARY KEY, pipeline_run_id UUID NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
 pipeline_job_id UUID, step_number INTEGER, event_type VARCHAR(48) NOT NULL, exception_type VARCHAR(255),
 raw_message TEXT, normalized_message TEXT NOT NULL, stack_trace TEXT, normalized_stack_root TEXT,
 fingerprint VARCHAR(64) NOT NULL, fingerprint_algorithm VARCHAR(16) NOT NULL, occurrence_count INTEGER NOT NULL,
 first_line INTEGER, last_line INTEGER, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_failure_event_scope UNIQUE(pipeline_run_id,fingerprint,step_number)
);
CREATE INDEX idx_failure_events_run ON failure_events(pipeline_run_id);
CREATE INDEX idx_failure_events_fingerprint ON failure_events(fingerprint);
