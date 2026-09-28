package com.axiom.application.analysis;

import com.axiom.analysis.correlation.ChangeRelevanceService;
import com.axiom.api.error.AnalysisPrerequisiteException;
import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.relevance.PersistedChangeRelevance;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineChangeAnalysisService {
    private final JdbcTemplate jdbc;
    private final FailureChangeContextService contexts;
    private final ChangeRelevanceService relevance;
    private final ChangeRelevancePersistenceService persistence;

    public PipelineChangeAnalysisService(
            JdbcTemplate jdbc,
            FailureChangeContextService contexts,
            ChangeRelevanceService relevance,
            ChangeRelevancePersistenceService persistence) {
        this.jdbc = jdbc;
        this.contexts = contexts;
        this.relevance = relevance;
        this.persistence = persistence;
    }

    @Transactional
    public AnalysisSummary analyze(UUID pipelineRunId) {
        ensureRunAndChanges(pipelineRunId);
        List<UUID> failures = jdbc.query(
                "select id from failure_events where pipeline_run_id=? order by first_line nulls last,id",
                (rs, row) -> rs.getObject("id", UUID.class),
                pipelineRunId);
        Map<ChangeRelevance, Integer> breakdown = new EnumMap<>(ChangeRelevance.class);
        for (ChangeRelevance outcome : ChangeRelevance.values()) breakdown.put(outcome, 0);
        for (UUID failureEventId : failures) {
            var context = contexts.build(pipelineRunId, failureEventId);
            var result = relevance.analyze(context);
            persistence.save(pipelineRunId, failureEventId, context.fingerprint(), result);
            breakdown.compute(result.relevance(), (key, value) -> value + 1);
        }
        return new AnalysisSummary(
                pipelineRunId,
                ChangeRelevanceService.VERSION,
                failures.size(),
                Map.copyOf(breakdown));
    }

    public List<PersistedChangeRelevance> get(UUID pipelineRunId) {
        return persistence.findByPipelineRunId(pipelineRunId);
    }

    public PersistedChangeRelevance get(UUID pipelineRunId, String fingerprint) {
        return persistence.findByPipelineRunIdAndFingerprint(pipelineRunId, fingerprint);
    }

    private void ensureRunAndChanges(UUID pipelineRunId) {
        boolean runExists = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pipeline_runs where id=?)",
                Boolean.class,
                pipelineRunId));
        if (!runExists) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
        boolean changesExist = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from git_change_sets where pipeline_run_id=?)",
                Boolean.class,
                pipelineRunId));
        if (!changesExist) {
            throw new AnalysisPrerequisiteException(
                    "Git changes must be ingested before change relevance can be analyzed for pipeline run "
                            + pipelineRunId
                            + ".");
        }
    }

    public record AnalysisSummary(
            UUID pipelineRunId,
            String analyzerVersion,
            int analyzedFailures,
            Map<ChangeRelevance, Integer> results) {}
}
