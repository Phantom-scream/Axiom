package com.axiom.application.analysis;

import com.axiom.api.error.AnalysisPrerequisiteException;
import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.application.testreport.RerunAnalysisService;
import com.axiom.config.ChangeAnalysisProperties;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.relevance.FailureChangeContext;
import com.axiom.domain.relevance.FailureChangeContext.ContextChangedFile;
import com.axiom.domain.relevance.FailureChangeContext.CorrelatedTest;
import com.axiom.domain.relevance.FailureChangeContext.TestHistory;
import com.axiom.domain.test.CorrelationStrength;
import com.axiom.domain.test.RerunTransition;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FailureChangeContextService {
    private final JdbcTemplate jdbc;
    private final FailureFingerprintHistoryService fingerprintHistory;
    private final RerunAnalysisService rerunAnalysis;
    private final ChangeAnalysisProperties properties;

    public FailureChangeContextService(
            JdbcTemplate jdbc,
            FailureFingerprintHistoryService fingerprintHistory,
            RerunAnalysisService rerunAnalysis,
            ChangeAnalysisProperties properties) {
        this.jdbc = jdbc;
        this.fingerprintHistory = fingerprintHistory;
        this.rerunAnalysis = rerunAnalysis;
        this.properties = properties;
    }

    public FailureChangeContext build(UUID pipelineRunId, UUID failureEventId) {
        RequiredEvidence required = requiredEvidence(pipelineRunId, failureEventId);
        ChangeSet changeSet = changeSet(pipelineRunId);
        List<ContextChangedFile> changedFiles = changedFiles(changeSet.id());
        CorrelatedTest correlatedTest = correlatedTest(failureEventId);
        List<RerunTransition> reruns = correlatedTest == null
                ? List.of()
                : rerunAnalysis.transitions(correlatedTest.stableTestId());
        return new FailureChangeContext(
                pipelineRunId,
                required.repositoryId(),
                failureEventId,
                required.fingerprint(),
                required.normalizedMessage(),
                required.exceptionType(),
                required.classification(),
                changeSet.baseSha(),
                changeSet.headSha(),
                changedFiles,
                correlatedTest,
                correlatedTest == null ? null : testHistory(correlatedTest.stableTestId()),
                fingerprintHistory.priorTo(pipelineRunId, required.fingerprint()),
                reruns);
    }

    private RequiredEvidence requiredEvidence(UUID pipelineRunId, UUID failureEventId) {
        List<RequiredEvidence> rows = jdbc.query(
                """
                select pr.repository_id, fe.fingerprint, fe.normalized_message, fe.exception_type,
                       fd.classification
                from pipeline_runs pr
                join failure_events fe on fe.pipeline_run_id = pr.id
                left join lateral (
                    select classification from failure_diagnoses
                    where failure_event_id = fe.id
                    order by created_at desc limit 1
                ) fd on true
                where pr.id = ? and fe.id = ?
                """,
                (rs, row) -> new RequiredEvidence(
                        rs.getObject("repository_id", UUID.class),
                        rs.getString("fingerprint"),
                        rs.getString("normalized_message"),
                        rs.getString("exception_type"),
                        rs.getString("classification") == null
                                ? FailureClassification.UNKNOWN
                                : FailureClassification.valueOf(rs.getString("classification"))),
                pipelineRunId,
                failureEventId);
        if (!rows.isEmpty()) return rows.getFirst();
        boolean runExists = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pipeline_runs where id=?)",
                Boolean.class,
                pipelineRunId));
        if (!runExists) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
        throw new ResourceNotFoundException(
                "Failure event " + failureEventId + " was not found in pipeline run " + pipelineRunId + ".");
    }

    private ChangeSet changeSet(UUID pipelineRunId) {
        List<ChangeSet> rows = jdbc.query(
                """
                select id, base_sha, head_sha from git_change_sets
                where pipeline_run_id = ? order by ingested_at desc limit 1
                """,
                (rs, row) -> new ChangeSet(
                        rs.getObject("id", UUID.class),
                        rs.getString("base_sha"),
                        rs.getString("head_sha")),
                pipelineRunId);
        if (rows.isEmpty()) {
            throw new AnalysisPrerequisiteException(
                    "Git changes must be ingested before change relevance can be analyzed for pipeline run "
                            + pipelineRunId
                            + ".");
        }
        return rows.getFirst();
    }

    private List<ContextChangedFile> changedFiles(UUID changeSetId) {
        return jdbc.query(
                """
                select id,path,previous_path,change_type,file_category,module_hint,
                       additions,deletions,changes
                from changed_files where change_set_id=? order by path
                """,
                (rs, row) -> new ContextChangedFile(
                        rs.getObject("id", UUID.class),
                        new ChangedFile(
                                rs.getString("path"),
                                rs.getString("previous_path"),
                                ChangeType.valueOf(rs.getString("change_type")),
                                FileCategory.valueOf(rs.getString("file_category")),
                                rs.getString("module_hint"),
                                rs.getInt("additions"),
                                rs.getInt("deletions"),
                                rs.getInt("changes"))),
                changeSetId);
    }

    private CorrelatedTest correlatedTest(UUID failureEventId) {
        List<CorrelatedTest> rows = jdbc.query(
                """
                select t.id, trim(t.stable_test_id) stable_test_id, t.class_name, t.test_name,
                       ts.name suite_name, t.correlation_strength
                from test_case_executions t
                join test_suites ts on ts.id=t.test_suite_id
                where t.correlated_failure_event_id=?
                  and t.correlation_strength in ('EXACT','STRONG')
                order by case t.correlation_strength when 'EXACT' then 0 else 1 end,
                         t.created_at desc, t.id
                limit 1
                """,
                (rs, row) -> new CorrelatedTest(
                        rs.getObject("id", UUID.class),
                        rs.getString("stable_test_id"),
                        rs.getString("class_name"),
                        rs.getString("test_name"),
                        rs.getString("suite_name"),
                        CorrelationStrength.valueOf(rs.getString("correlation_strength"))),
                failureEventId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private TestHistory testHistory(String stableTestId) {
        List<String> statuses = jdbc.query(
                """
                select t.status from test_case_executions t
                join pipeline_runs pr on pr.id=t.pipeline_run_id
                where trim(t.stable_test_id)=?
                order by coalesce(pr.finished_at,pr.started_at,pr.ingested_at,pr.created_at) desc,
                         pr.external_run_id desc,pr.attempt desc
                limit ?
                """,
                (rs, row) -> rs.getString("status"),
                stableTestId,
                properties.effectiveHistoryLookback());
        return new TestHistory(
                statuses.size(),
                count(statuses, "PASSED"),
                count(statuses, "FAILED"),
                count(statuses, "ERROR"),
                count(statuses, "SKIPPED"));
    }

    private int count(List<String> statuses, String expected) {
        return (int) statuses.stream().filter(expected::equals).count();
    }

    private record RequiredEvidence(
            UUID repositoryId,
            String fingerprint,
            String normalizedMessage,
            String exceptionType,
            FailureClassification classification) {}

    private record ChangeSet(UUID id, String baseSha, String headSha) {}
}
