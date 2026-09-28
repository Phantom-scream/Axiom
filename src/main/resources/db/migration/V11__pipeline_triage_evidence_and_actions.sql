ALTER TABLE pipeline_triage_results
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE failure_triage_entries
    ADD COLUMN classification VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN change_relevance VARCHAR(32),
    ADD COLUMN historical_occurrence_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN correlated_tests INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN test_stability VARCHAR(32),
    ADD COLUMN unchanged_rerun_passed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN repeated_same_failure BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE triage_evidence (
    id UUID PRIMARY KEY,
    triage_result_id UUID NOT NULL REFERENCES pipeline_triage_results(id) ON DELETE CASCADE,
    failure_event_id UUID REFERENCES failure_events(id) ON DELETE CASCADE,
    code VARCHAR(128) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    description TEXT NOT NULL,
    metadata JSONB
);

CREATE TABLE triage_actions (
    id UUID PRIMARY KEY,
    triage_result_id UUID NOT NULL REFERENCES pipeline_triage_results(id) ON DELETE CASCADE,
    priority INTEGER NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    reason TEXT NOT NULL,
    CONSTRAINT uq_triage_action_priority UNIQUE (triage_result_id, priority)
);

CREATE INDEX idx_triage_evidence_result ON triage_evidence(triage_result_id);
CREATE INDEX idx_triage_actions_result ON triage_actions(triage_result_id);
