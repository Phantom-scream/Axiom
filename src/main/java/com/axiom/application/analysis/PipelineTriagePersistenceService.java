package com.axiom.application.analysis;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.triage.ActionType;
import com.axiom.domain.triage.FailureRole;
import com.axiom.domain.triage.FailureTriageEntry;
import com.axiom.domain.triage.PipelineTriageResult;
import com.axiom.domain.triage.RecommendedAction;
import com.axiom.domain.triage.RerunRecommendation;
import com.axiom.domain.triage.TriageEvidence;
import com.axiom.domain.triage.TriageEvidencePriority;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PipelineTriagePersistenceService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public PipelineTriagePersistenceService(
            JdbcTemplate jdbc, Clock clock, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PipelineTriageResult save(
            UUID pipelineRunId,
            String status,
            String version,
            PipelineTriageService.Result result,
            List<RecommendedAction> actions) {
        UUID triageId = jdbc.query(
                """
                insert into pipeline_triage_results(
                    id,pipeline_run_id,primary_failure_event_id,primary_fingerprint,
                    primary_classification,rerun_recommendation,rerun_confidence,
                    summary,triage_version,created_at,status)
                values(?,?,?,?,?,?,?,?,?,?,?)
                on conflict(pipeline_run_id,triage_version) do update set
                    primary_failure_event_id=excluded.primary_failure_event_id,
                    primary_fingerprint=excluded.primary_fingerprint,
                    primary_classification=excluded.primary_classification,
                    rerun_recommendation=excluded.rerun_recommendation,
                    rerun_confidence=excluded.rerun_confidence,
                    summary=excluded.summary,
                    created_at=excluded.created_at,
                    status=excluded.status
                returning id
                """,
                rs -> {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                },
                UUID.randomUUID(),
                pipelineRunId,
                result.primaryFailureEventId(),
                result.primaryFingerprint(),
                result.primaryClassification() == null
                        ? null
                        : result.primaryClassification().name(),
                result.rerunRecommendation(),
                result.rerunConfidence(),
                result.summary(),
                version,
                Timestamp.from(clock.instant()),
                status);

        jdbc.update("delete from triage_actions where triage_result_id=?", triageId);
        jdbc.update("delete from triage_evidence where triage_result_id=?", triageId);
        jdbc.update("delete from failure_triage_entries where triage_result_id=?", triageId);
        for (FailureTriageEntry entry : result.failureRankings()) {
            jdbc.update(
                    """
                    insert into failure_triage_entries(
                        id,triage_result_id,failure_event_id,fingerprint,failure_role,
                        importance_score,classification,change_relevance,
                        historical_occurrence_count,correlated_tests,test_stability,
                        unchanged_rerun_passed,repeated_same_failure)
                    values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    triageId,
                    entry.failureEventId(),
                    entry.fingerprint(),
                    entry.role().name(),
                    entry.importanceScore(),
                    entry.classification().name(),
                    entry.changeRelevance() == null ? null : entry.changeRelevance().name(),
                    entry.historicalOccurrenceCount(),
                    entry.correlatedTests(),
                    entry.testStability() == null ? null : entry.testStability().name(),
                    entry.unchangedRerunPassed(),
                    entry.repeatedSameFailure());
        }
        for (TriageEvidence evidence : result.evidence()) {
            jdbc.update(
                    """
                    insert into triage_evidence(
                        id,triage_result_id,failure_event_id,code,priority,weight,
                        description,metadata)
                    values(?,?,?,?,?,?,?,cast(? as jsonb))
                    """,
                    UUID.randomUUID(),
                    triageId,
                    evidence.failureEventId(),
                    evidence.code(),
                    evidence.priority().name(),
                    evidence.weight(),
                    evidence.description(),
                    objectMapper.writeValueAsString(evidence.metadata()));
        }
        for (RecommendedAction action : actions) {
            jdbc.update(
                    """
                    insert into triage_actions(
                        id,triage_result_id,priority,action_type,description,reason)
                    values(?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    triageId,
                    action.priority(),
                    action.type().name(),
                    action.description(),
                    action.reason());
        }
        return findByPipelineRunId(pipelineRunId, version);
    }

    public PipelineTriageResult findByPipelineRunId(UUID pipelineRunId, String version) {
        ensureRunExists(pipelineRunId);
        List<ResultRow> rows = jdbc.query(
                """
                select id,pipeline_run_id,status,primary_failure_event_id,primary_fingerprint,
                       primary_classification,rerun_recommendation,rerun_confidence,
                       summary,triage_version,created_at
                from pipeline_triage_results
                where pipeline_run_id=? and triage_version=?
                """,
                (rs, row) -> new ResultRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("pipeline_run_id", UUID.class),
                        rs.getString("status"),
                        rs.getObject("primary_failure_event_id", UUID.class),
                        rs.getString("primary_fingerprint"),
                        nullableClassification(rs.getString("primary_classification")),
                        RerunRecommendation.valueOf(rs.getString("rerun_recommendation")),
                        rs.getDouble("rerun_confidence"),
                        rs.getString("summary"),
                        rs.getString("triage_version"),
                        rs.getTimestamp("created_at").toInstant()),
                pipelineRunId,
                version);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Triage " + version + " has not been computed for pipeline run " + pipelineRunId + ".");
        }
        ResultRow row = rows.getFirst();
        List<FailureTriageEntry> failures = failures(row.id());
        FailureRole primaryRole = failures.stream()
                .filter(entry -> entry.failureEventId().equals(row.primaryFailureEventId()))
                .map(FailureTriageEntry::role)
                .findFirst()
                .orElse(FailureRole.UNKNOWN);
        ChangeRelevance primaryRelevance = failures.stream()
                .filter(entry -> entry.failureEventId().equals(row.primaryFailureEventId()))
                .map(FailureTriageEntry::changeRelevance)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new PipelineTriageResult(
                row.id(),
                row.pipelineRunId(),
                row.status(),
                row.primaryFailureEventId(),
                row.primaryFingerprint(),
                primaryRole,
                row.primaryClassification(),
                primaryRelevance,
                row.rerunRecommendation(),
                row.rerunConfidence(),
                row.summary(),
                row.triageVersion(),
                row.createdAt(),
                actions(row.id()),
                failures,
                evidence(row.id()));
    }

    private List<FailureTriageEntry> failures(UUID triageId) {
        return jdbc.query(
                """
                select failure_event_id,fingerprint,classification,failure_role,
                       importance_score,change_relevance,historical_occurrence_count,
                       correlated_tests,test_stability,unchanged_rerun_passed,
                       repeated_same_failure
                from failure_triage_entries where triage_result_id=?
                order by importance_score desc,fingerprint
                """,
                (rs, row) -> new FailureTriageEntry(
                        rs.getObject("failure_event_id", UUID.class),
                        rs.getString("fingerprint"),
                        FailureClassification.valueOf(rs.getString("classification")),
                        FailureRole.valueOf(rs.getString("failure_role")),
                        rs.getDouble("importance_score"),
                        nullableRelevance(rs.getString("change_relevance")),
                        rs.getInt("historical_occurrence_count"),
                        rs.getInt("correlated_tests"),
                        nullableStability(rs.getString("test_stability")),
                        rs.getBoolean("unchanged_rerun_passed"),
                        rs.getBoolean("repeated_same_failure")),
                triageId);
    }

    private List<TriageEvidence> evidence(UUID triageId) {
        return jdbc.query(
                """
                select failure_event_id,code,priority,weight,description,metadata::text metadata
                from triage_evidence where triage_result_id=? order by code,id
                """,
                (rs, row) -> new TriageEvidence(
                        rs.getObject("failure_event_id", UUID.class),
                        rs.getString("code"),
                        TriageEvidencePriority.valueOf(rs.getString("priority")),
                        rs.getDouble("weight"),
                        rs.getString("description"),
                        objectMapper.readValue(
                                rs.getString("metadata"), new TypeReference<Map<String, String>>() {})),
                triageId);
    }

    private List<RecommendedAction> actions(UUID triageId) {
        return jdbc.query(
                """
                select priority,action_type,description,reason from triage_actions
                where triage_result_id=? order by priority
                """,
                (rs, row) -> new RecommendedAction(
                        rs.getInt("priority"),
                        ActionType.valueOf(rs.getString("action_type")),
                        rs.getString("description"),
                        rs.getString("reason")),
                triageId);
    }

    private void ensureRunExists(UUID pipelineRunId) {
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pipeline_runs where id=?)",
                Boolean.class,
                pipelineRunId));
        if (!exists) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
    }

    private FailureClassification nullableClassification(String value) {
        return value == null ? null : FailureClassification.valueOf(value);
    }

    private ChangeRelevance nullableRelevance(String value) {
        return value == null ? null : ChangeRelevance.valueOf(value);
    }

    private TestStabilityClass nullableStability(String value) {
        return value == null ? null : TestStabilityClass.valueOf(value);
    }

    private record ResultRow(
            UUID id,
            UUID pipelineRunId,
            String status,
            UUID primaryFailureEventId,
            String primaryFingerprint,
            FailureClassification primaryClassification,
            RerunRecommendation rerunRecommendation,
            double rerunConfidence,
            String summary,
            String triageVersion,
            Instant createdAt) {}
}
