CREATE TABLE change_relevance_evidence (
    id UUID PRIMARY KEY,
    relevance_result_id UUID NOT NULL REFERENCES change_relevance_results(id) ON DELETE CASCADE,
    code VARCHAR(128) NOT NULL,
    priority VARCHAR(32) NOT NULL,
    weight DOUBLE PRECISION NOT NULL,
    description TEXT NOT NULL,
    source VARCHAR(255),
    metadata JSONB
);

CREATE TABLE relevance_related_files (
    relevance_result_id UUID NOT NULL REFERENCES change_relevance_results(id) ON DELETE CASCADE,
    changed_file_id UUID NOT NULL REFERENCES changed_files(id) ON DELETE CASCADE,
    relationship VARCHAR(128) NOT NULL,
    weight DOUBLE PRECISION,
    PRIMARY KEY (relevance_result_id, changed_file_id, relationship)
);

CREATE INDEX idx_relevance_evidence_result ON change_relevance_evidence(relevance_result_id);
CREATE INDEX idx_relevance_related_file ON relevance_related_files(changed_file_id);
