package com.axiom.application.analysis;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.application.testreport.RerunAnalysisService;
import com.axiom.config.TriageProperties;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.PersistedChangeRelevance;
import com.axiom.domain.relevance.RelevanceEvidencePriority;
import com.axiom.domain.test.RerunTransitionType;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.triage.PipelineTriageResult;
import com.axiom.domain.triage.RecommendedAction;
import com.axiom.domain.triage.TriageEvidence;
import com.axiom.domain.triage.TriageEvidencePriority;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PipelineTriageApplicationService {
    private final JdbcTemplate jdbc;
    private final PipelineTriageService triage;
    private final DeveloperActionService developerActions;
    private final PipelineTriagePersistenceService persistence;
    private final ChangeRelevancePersistenceService relevancePersistence;
    private final FailureFingerprintHistoryService fingerprintHistory;
    private final RerunAnalysisService rerunAnalysis;
    private final TriageProperties properties;

    public PipelineTriageApplicationService(
            JdbcTemplate jdbc,
            PipelineTriageService triage,
            DeveloperActionService developerActions,
            PipelineTriagePersistenceService persistence,
            ChangeRelevancePersistenceService relevancePersistence,
            FailureFingerprintHistoryService fingerprintHistory,
            RerunAnalysisService rerunAnalysis,
            TriageProperties properties) {
        this.jdbc = jdbc;
        this.triage = triage;
        this.developerActions = developerActions;
        this.persistence = persistence;
        this.relevancePersistence = relevancePersistence;
        this.fingerprintHistory = fingerprintHistory;
        this.rerunAnalysis = rerunAnalysis;
        this.properties = properties;
    }

    @Transactional
    public PipelineTriageResult compute(UUID pipelineRunId) {
        RunState run = runState(pipelineRunId);
        List<BaseFailure> baseFailures = failures(pipelineRunId);
        Map<UUID, PersistedChangeRelevance> relevance = relevancePersistence
                .findByPipelineRunId(pipelineRunId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        PersistedChangeRelevance::failureEventId, value -> value));
        Map<UUID, List<String>> stableTests = correlatedStableTests(pipelineRunId);
        Map<String, TestStabilityClass> stability = stability(pipelineRunId);
        Map<String, RerunFacts> reruns = new HashMap<>();
        Map<String, Integer> history = new HashMap<>();

        List<PipelineTriageService.Failure> inputs = new ArrayList<>();
        for (BaseFailure failure : baseFailures) {
            List<String> stableIds = stableTests.getOrDefault(failure.id(), List.of());
            boolean unchangedPass = false;
            boolean repeatedSameFailure = false;
            TestStabilityClass testStability = null;
            for (String stableId : stableIds) {
                RerunFacts facts = reruns.computeIfAbsent(stableId, this::rerunFacts);
                unchangedPass |= facts.unchangedPass();
                repeatedSameFailure |= facts.repeatedSameFailure();
                TestStabilityClass candidate = stability.get(stableId);
                if (candidate != null
                        && (testStability == null
                                || stabilityPriority(candidate) > stabilityPriority(testStability))) {
                    testStability = candidate;
                }
            }
            int occurrences = history.computeIfAbsent(
                    failure.fingerprint(),
                    fingerprint -> fingerprintHistory
                            .priorTo(pipelineRunId, fingerprint)
                            .priorOccurrences());
            PersistedChangeRelevance persistedRelevance = relevance.get(failure.id());
            inputs.add(new PipelineTriageService.Failure(
                    failure.id(),
                    failure.fingerprint(),
                    failure.classification(),
                    isGeneric(failure),
                    failure.firstLine(),
                    occurrences,
                    persistedRelevance == null ? null : persistedRelevance.relevance(),
                    stableIds.size(),
                    testStability,
                    unchangedPass,
                    repeatedSameFailure));
        }

        PipelineTriageService.Result result;
        String status;
        if (isSuccessful(run)) {
            result = withSummary(
                    triage.triage(List.of()),
                    "The pipeline completed successfully; failure triage is not required.");
            status = "NO_TRIAGE_NEEDED";
        } else if (isCancelled(run)) {
            result = withSummary(
                    triage.triage(List.of()),
                    "The pipeline was cancelled; Axiom will not fabricate failure triage.");
            status = "INSUFFICIENT_EVIDENCE";
        } else {
            result = triage.triage(inputs);
            status = inputs.isEmpty() ? "INSUFFICIENT_EVIDENCE" : "COMPLETED";
        }

        result = withRelevanceEvidence(result, relevance);
        boolean relatedSourceFiles = result.primaryFailureEventId() != null
                && hasRelatedSourceFiles(result.primaryFailureEventId());
        List<RecommendedAction> actions = status.equals("COMPLETED")
                ? developerActions.generate(result, relatedSourceFiles)
                : List.of();
        return persistence.save(
                pipelineRunId, status, properties.effectiveVersion(), result, actions);
    }

    public PipelineTriageResult get(UUID pipelineRunId) {
        return persistence.findByPipelineRunId(pipelineRunId, properties.effectiveVersion());
    }

    private RunState runState(UUID pipelineRunId) {
        List<RunState> rows = jdbc.query(
                "select status,conclusion from pipeline_runs where id=?",
                (rs, row) -> new RunState(rs.getString("status"), rs.getString("conclusion")),
                pipelineRunId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
        return rows.getFirst();
    }

    private List<BaseFailure> failures(UUID pipelineRunId) {
        return jdbc.query(
                """
                select fe.id,fe.fingerprint,fe.event_type,fe.normalized_message,fe.exception_type,
                       fe.first_line,coalesce(fd.classification,'UNKNOWN') classification
                from failure_events fe
                left join lateral (
                    select classification from failure_diagnoses
                    where failure_event_id=fe.id order by created_at desc limit 1
                ) fd on true
                where fe.pipeline_run_id=?
                order by fe.first_line nulls last,fe.id
                """,
                (rs, row) -> new BaseFailure(
                        rs.getObject("id", UUID.class),
                        rs.getString("fingerprint"),
                        rs.getString("event_type"),
                        rs.getString("normalized_message"),
                        rs.getString("exception_type"),
                        (Integer) rs.getObject("first_line"),
                        FailureClassification.valueOf(rs.getString("classification"))),
                pipelineRunId);
    }

    private Map<UUID, List<String>> correlatedStableTests(UUID pipelineRunId) {
        Map<UUID, List<String>> result = new HashMap<>();
        jdbc.query(
                        """
                        select correlated_failure_event_id,trim(stable_test_id) stable_test_id
                        from test_case_executions
                        where pipeline_run_id=? and correlated_failure_event_id is not null
                          and correlation_strength in ('EXACT','STRONG')
                        order by correlated_failure_event_id,stable_test_id
                        """,
                        (rs, row) -> new CorrelatedTest(
                                rs.getObject("correlated_failure_event_id", UUID.class),
                                rs.getString("stable_test_id")),
                        pipelineRunId)
                .forEach(test -> result.computeIfAbsent(test.failureEventId(), ignored -> new ArrayList<>())
                        .add(test.stableTestId()));
        result.replaceAll((key, value) -> value.stream().distinct().toList());
        return result;
    }

    private Map<String, TestStabilityClass> stability(UUID pipelineRunId) {
        Map<String, TestStabilityClass> result = new HashMap<>();
        jdbc.query(
                        """
                        select distinct trim(s.stable_test_id) stable_test_id,s.stability_class
                        from test_case_executions t
                        join test_stability_snapshots s on s.stable_test_id=t.stable_test_id
                        where t.pipeline_run_id=? and t.correlated_failure_event_id is not null
                          and s.classifier_version='stability-v1'
                        """,
                        (rs, row) -> new Stability(
                                rs.getString("stable_test_id"),
                                TestStabilityClass.valueOf(rs.getString("stability_class"))),
                        pipelineRunId)
                .forEach(value -> result.put(value.stableTestId(), value.classification()));
        return result;
    }

    private RerunFacts rerunFacts(String stableTestId) {
        var transitions = rerunAnalysis.transitions(stableTestId);
        boolean unchangedPass = transitions.stream()
                .anyMatch(transition -> transition.sameCommitSha()
                        && (transition.transitionType() == RerunTransitionType.FAIL_TO_PASS
                                || transition.transitionType()
                                        == RerunTransitionType.ERROR_TO_PASS));
        boolean repeated = transitions.stream()
                .anyMatch(transition -> transition.sameCommitSha()
                        && (transition.transitionType() == RerunTransitionType.FAIL_TO_FAIL
                                || transition.transitionType()
                                        == RerunTransitionType.ERROR_TO_ERROR));
        return new RerunFacts(unchangedPass, repeated);
    }

    private PipelineTriageService.Result withRelevanceEvidence(
            PipelineTriageService.Result result,
            Map<UUID, PersistedChangeRelevance> relevance) {
        List<TriageEvidence> evidence = new ArrayList<>(result.evidence());
        relevance.values().forEach(value -> value.evidence().forEach(item -> evidence.add(
                new TriageEvidence(
                        value.failureEventId(),
                        "CHANGE_RELEVANCE_" + item.code(),
                        priority(item.priority()),
                        item.weight(),
                        item.description(),
                        item.metadata()))));
        return new PipelineTriageService.Result(
                result.primaryFailureEventId(),
                result.primaryFingerprint(),
                result.primaryRole(),
                result.primaryClassification(),
                result.rerunRecommendation(),
                result.rerunConfidence(),
                result.summary(),
                result.failureRankings(),
                List.copyOf(evidence));
    }

    private PipelineTriageService.Result withSummary(
            PipelineTriageService.Result result, String summary) {
        return new PipelineTriageService.Result(
                result.primaryFailureEventId(),
                result.primaryFingerprint(),
                result.primaryRole(),
                result.primaryClassification(),
                result.rerunRecommendation(),
                result.rerunConfidence(),
                summary,
                result.failureRankings(),
                result.evidence());
    }

    private TriageEvidencePriority priority(RelevanceEvidencePriority priority) {
        return switch (priority) {
            case STRONG -> TriageEvidencePriority.STRONG;
            case MEDIUM -> TriageEvidencePriority.MEDIUM;
            case WEAK -> TriageEvidencePriority.WEAK;
            case COUNTER -> TriageEvidencePriority.COUNTER;
        };
    }

    private boolean hasRelatedSourceFiles(UUID failureEventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select exists(
                    select 1 from change_relevance_results r
                    join relevance_related_files rf on rf.relevance_result_id=r.id
                    join changed_files cf on cf.id=rf.changed_file_id
                    where r.failure_event_id=?
                      and cf.file_category in ('PRODUCTION_SOURCE','TEST_SOURCE'))
                """,
                Boolean.class,
                failureEventId));
    }

    private boolean isGeneric(BaseFailure failure) {
        String message = failure.normalizedMessage() == null
                ? ""
                : failure.normalizedMessage().toLowerCase(Locale.ROOT);
        String type = failure.eventType() == null
                ? ""
                : failure.eventType().toLowerCase(Locale.ROOT);
        return message.matches(".*(process completed with exit code|command failed|build failed).*?")
                || type.contains("command_exit")
                || type.contains("build_summary");
    }

    private boolean isSuccessful(RunState run) {
        return "SUCCESS".equalsIgnoreCase(run.conclusion());
    }

    private boolean isCancelled(RunState run) {
        return "CANCELLED".equalsIgnoreCase(run.conclusion());
    }

    private int stabilityPriority(TestStabilityClass stability) {
        return switch (stability) {
            case CONSISTENTLY_FAILING -> 6;
            case FLAKY -> 5;
            case SUSPECTED_FLAKY -> 4;
            case INSUFFICIENT_HISTORY -> 3;
            case LIKELY_STABLE -> 2;
            case STABLE -> 1;
        };
    }

    private record RunState(String status, String conclusion) {}

    private record BaseFailure(
            UUID id,
            String fingerprint,
            String eventType,
            String normalizedMessage,
            String exceptionType,
            Integer firstLine,
            FailureClassification classification) {}

    private record CorrelatedTest(UUID failureEventId, String stableTestId) {}

    private record Stability(String stableTestId, TestStabilityClass classification) {}

    private record RerunFacts(boolean unchangedPass, boolean repeatedSameFailure) {}
}
