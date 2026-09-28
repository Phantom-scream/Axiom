package com.axiom.application.analysis;

import com.axiom.config.ChangeAnalysisProperties;
import com.axiom.domain.relevance.FailureChangeContext.FingerprintHistory;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FailureFingerprintHistoryService {
    private final JdbcTemplate jdbc;
    private final ChangeAnalysisProperties properties;

    public FailureFingerprintHistoryService(
            JdbcTemplate jdbc, ChangeAnalysisProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public FingerprintHistory priorTo(UUID pipelineRunId, String fingerprint) {
        List<Occurrence> occurrences = jdbc.query(
                """
                with current_run as (
                    select repository_id,
                           coalesce(finished_at, started_at, ingested_at, created_at) as occurred_at
                    from pipeline_runs where id = ?
                )
                select fe.pipeline_run_id, fe.occurrence_count
                from failure_events fe
                join pipeline_runs pr on pr.id = fe.pipeline_run_id
                join current_run current on current.repository_id = pr.repository_id
                where fe.fingerprint = ?
                  and fe.pipeline_run_id <> ?
                  and coalesce(pr.finished_at, pr.started_at, pr.ingested_at, pr.created_at)
                      < current.occurred_at
                order by coalesce(pr.finished_at, pr.started_at, pr.ingested_at, pr.created_at) desc,
                         pr.external_run_id desc, pr.attempt desc
                limit ?
                """,
                (rs, row) -> new Occurrence(
                        rs.getObject("pipeline_run_id", UUID.class),
                        rs.getInt("occurrence_count")),
                pipelineRunId,
                fingerprint,
                pipelineRunId,
                properties.effectiveHistoryLookback());
        int count = occurrences.stream().mapToInt(Occurrence::count).sum();
        List<UUID> runIds = List.copyOf(new LinkedHashSet<>(
                occurrences.stream().map(Occurrence::pipelineRunId).toList()));
        return new FingerprintHistory(count, runIds);
    }

    private record Occurrence(UUID pipelineRunId, int count) {}
}
