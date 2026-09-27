ALTER TABLE pipeline_runs ADD COLUMN base_sha VARCHAR(128);
ALTER TABLE pipeline_runs ADD COLUMN event_name VARCHAR(64);
