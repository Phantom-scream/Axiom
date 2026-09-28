package com.axiom.application.analysis;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.config.TriageProperties;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PipelineAnalysisStateService {
    private final JdbcTemplate jdbc;
    private final TriageProperties triageProperties;

    public PipelineAnalysisStateService(JdbcTemplate jdbc, TriageProperties triageProperties) {
        this.jdbc = jdbc;
        this.triageProperties = triageProperties;
    }

    public void requireRun(UUID pipelineRunId) {
        if (!exists("pipeline_runs", "id", pipelineRunId)) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
    }

    public boolean hasStoredLog(UUID pipelineRunId) {
        return exists("pipeline_logs", "pipeline_run_id", pipelineRunId);
    }

    public int failureCount(UUID pipelineRunId) {
        return count("failure_events", "pipeline_run_id", pipelineRunId);
    }

    public int currentDiagnosisCount(UUID pipelineRunId) {
        return jdbc.queryForObject(
                "select count(*) from failure_diagnoses where pipeline_run_id=? and classifier_version=?",
                Integer.class,
                pipelineRunId,
                PipelineDiagnosisService.VERSION);
    }

    public int testExecutionCount(UUID pipelineRunId) {
        return count("test_case_executions", "pipeline_run_id", pipelineRunId);
    }

    public int failedTestExecutionCount(UUID pipelineRunId) {
        return jdbc.queryForObject(
                "select count(*) from test_case_executions where pipeline_run_id=? and status in ('FAILED','ERROR')",
                Integer.class,
                pipelineRunId);
    }

    public boolean correlationsCurrent(UUID pipelineRunId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select not exists(
                    select 1 from test_case_executions
                    where pipeline_run_id=? and status in ('FAILED','ERROR')
                      and correlation_strength is null)
                """,
                Boolean.class,
                pipelineRunId));
    }

    public int stableTestCount(UUID pipelineRunId) {
        return jdbc.queryForObject(
                "select count(distinct stable_test_id) from test_case_executions where pipeline_run_id=?",
                Integer.class,
                pipelineRunId);
    }

    public int stabilitySnapshotCount(UUID pipelineRunId) {
        return jdbc.queryForObject(
                """
                select count(distinct trim(s.stable_test_id))
                from test_stability_snapshots s
                join test_case_executions t on t.stable_test_id=s.stable_test_id
                where t.pipeline_run_id=? and s.classifier_version='stability-v1'
                """,
                Integer.class,
                pipelineRunId);
    }

    public boolean hasComparisonMetadata(UUID pipelineRunId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select coalesce(base_sha,'')<>'' and coalesce(commit_sha,'')<>'' from pipeline_runs where id=?",
                Boolean.class,
                pipelineRunId));
    }

    public boolean hasChangeSet(UUID pipelineRunId) {
        return exists("git_change_sets", "pipeline_run_id", pipelineRunId);
    }

    public int currentRelevanceCount(UUID pipelineRunId) {
        return jdbc.queryForObject(
                "select count(*) from change_relevance_results where pipeline_run_id=? and analyzer_version='change-relevance-v1'",
                Integer.class,
                pipelineRunId);
    }

    public boolean hasCurrentTriage(UUID pipelineRunId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pipeline_triage_results where pipeline_run_id=? and triage_version=?)",
                Boolean.class,
                pipelineRunId,
                triageProperties.effectiveVersion()));
    }

    private int count(String table, String column, UUID value) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + "=?", Integer.class, value);
    }

    private boolean exists(String table, String column, UUID value) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from " + table + " where " + column + "=?)",
                Boolean.class,
                value));
    }
}
