package com.axiom.application.analysis;

import com.axiom.application.pipeline.GitChangeIngestionService;
import com.axiom.application.testreport.TestFailureCorrelationService;
import com.axiom.application.testreport.TestStabilitySnapshotService;
import com.axiom.domain.analysis.AnalysisStage;
import com.axiom.domain.analysis.AnalysisStageResult;
import com.axiom.domain.analysis.AnalysisStageStatus;
import com.axiom.domain.analysis.PipelineAnalysisResult;
import com.axiom.integrations.github.exception.GitHubIntegrationException;
import com.axiom.observability.AxiomMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PipelineAnalysisOrchestrator {
    private static final Logger LOG = LoggerFactory.getLogger(PipelineAnalysisOrchestrator.class);

    private final PipelineAnalysisStateService state;
    private final PipelineLogAnalysisService logs;
    private final PipelineDiagnosisService diagnoses;
    private final TestFailureCorrelationService correlations;
    private final TestStabilitySnapshotService stability;
    private final GitChangeIngestionService changes;
    private final PipelineChangeAnalysisService relevance;
    private final PipelineTriageApplicationService triage;
    private final AxiomMetrics metrics;

    @Autowired
    public PipelineAnalysisOrchestrator(
            PipelineAnalysisStateService state,
            PipelineLogAnalysisService logs,
            PipelineDiagnosisService diagnoses,
            TestFailureCorrelationService correlations,
            TestStabilitySnapshotService stability,
            GitChangeIngestionService changes,
            PipelineChangeAnalysisService relevance,
            PipelineTriageApplicationService triage,
            AxiomMetrics metrics) {
        this.state = state;
        this.logs = logs;
        this.diagnoses = diagnoses;
        this.correlations = correlations;
        this.stability = stability;
        this.changes = changes;
        this.relevance = relevance;
        this.triage = triage;
        this.metrics = metrics;
    }

    public PipelineAnalysisOrchestrator(
            PipelineAnalysisStateService state,
            PipelineLogAnalysisService logs,
            PipelineDiagnosisService diagnoses,
            TestFailureCorrelationService correlations,
            TestStabilitySnapshotService stability,
            GitChangeIngestionService changes,
            PipelineChangeAnalysisService relevance,
            PipelineTriageApplicationService triage) {
        this(
                state,
                logs,
                diagnoses,
                correlations,
                stability,
                changes,
                relevance,
                triage,
                new AxiomMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
    }

    public PipelineAnalysisResult analyze(UUID pipelineRunId, boolean recompute) {
        Timer.Sample analysisTimer = metrics.start();
        state.requireRun(pipelineRunId);
        List<AnalysisStageResult> results = new ArrayList<>();

        results.add(measure(() -> logStage(pipelineRunId, recompute)));
        results.add(measure(() -> diagnosisStage(pipelineRunId, recompute)));
        results.add(measure(() -> correlationStage(pipelineRunId, recompute)));
        results.add(measure(() -> stabilityStage(pipelineRunId, recompute)));
        results.add(measure(() -> changeIngestionStage(pipelineRunId, recompute)));
        results.add(measure(() -> relevanceStage(pipelineRunId, recompute)));
        results.add(measure(() -> triageStage(pipelineRunId, recompute)));

        PipelineAnalysisResult result = new PipelineAnalysisResult(
                pipelineRunId, recompute, results, state.hasCurrentTriage(pipelineRunId));
        metrics.analysisCompleted(
                analysisTimer,
                results.stream().anyMatch(value -> value.status() == AnalysisStageStatus.FAILED)
                        ? "partial_failure"
                        : "completed");
        return result;
    }

    private AnalysisStageResult logStage(UUID runId, boolean recompute) {
        if (!recompute && state.failureCount(runId) > 0) {
            return AnalysisStageResult.reused(
                    AnalysisStage.LOG_PROCESSING, "Existing extracted failure events reused.");
        }
        if (!state.hasStoredLog(runId)) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.LOG_PROCESSING,
                    AnalysisStageStatus.SKIPPED_NO_DATA,
                    "No stored pipeline log is available.");
        }
        return execute(
                AnalysisStage.LOG_PROCESSING,
                () -> logs.process(runId),
                "Stored pipeline logs processed.");
    }

    private AnalysisStageResult diagnosisStage(UUID runId, boolean recompute) {
        int failures = state.failureCount(runId);
        if (failures == 0) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.DIAGNOSIS,
                    AnalysisStageStatus.SKIPPED_NO_DATA,
                    "No extracted failure events are available.");
        }
        if (!recompute && state.currentDiagnosisCount(runId) >= failures) {
            return AnalysisStageResult.reused(
                    AnalysisStage.DIAGNOSIS, "Existing current-version diagnoses reused.");
        }
        return execute(
                AnalysisStage.DIAGNOSIS,
                () -> diagnoses.diagnose(runId),
                "Failure diagnoses computed.");
    }

    private AnalysisStageResult correlationStage(UUID runId, boolean recompute) {
        if (state.testExecutionCount(runId) == 0) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.TEST_CORRELATION,
                    AnalysisStageStatus.SKIPPED_NO_DATA,
                    "No structured test executions are available.");
        }
        if (state.failedTestExecutionCount(runId) == 0) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.TEST_CORRELATION,
                    AnalysisStageStatus.SKIPPED_NOT_APPLICABLE,
                    "The structured test reports contain no failed executions.");
        }
        if (!recompute && state.correlationsCurrent(runId)) {
            return AnalysisStageResult.reused(
                    AnalysisStage.TEST_CORRELATION, "Existing deterministic correlations reused.");
        }
        return execute(
                AnalysisStage.TEST_CORRELATION,
                () -> correlations.correlate(runId),
                "Structured test failures correlated.");
    }

    private AnalysisStageResult stabilityStage(UUID runId, boolean recompute) {
        int stableTests = state.stableTestCount(runId);
        if (stableTests == 0) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.TEST_STABILITY,
                    AnalysisStageStatus.SKIPPED_NO_DATA,
                    "No stable test history applies to this pipeline.");
        }
        if (!recompute && state.stabilitySnapshotCount(runId) >= stableTests) {
            return AnalysisStageResult.reused(
                    AnalysisStage.TEST_STABILITY, "Existing stability-v1 snapshots reused.");
        }
        return execute(
                AnalysisStage.TEST_STABILITY,
                () -> stability.computeForPipeline(runId),
                "Stability-v1 snapshots computed.");
    }

    private AnalysisStageResult changeIngestionStage(UUID runId, boolean recompute) {
        if (!recompute && state.hasChangeSet(runId)) {
            return AnalysisStageResult.reused(
                    AnalysisStage.CHANGE_INGESTION, "Existing normalized Git change set reused.");
        }
        if (!state.hasComparisonMetadata(runId)) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.CHANGE_INGESTION,
                    AnalysisStageStatus.SKIPPED_NOT_APPLICABLE,
                    "Reliable base/head comparison metadata is unavailable.");
        }
        return execute(
                AnalysisStage.CHANGE_INGESTION,
                () -> changes.ingest(runId),
                "Git changes ingested and classified.");
    }

    private AnalysisStageResult relevanceStage(UUID runId, boolean recompute) {
        if (!state.hasChangeSet(runId)) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.CHANGE_RELEVANCE,
                    AnalysisStageStatus.SKIPPED_NO_DATA,
                    "No persisted Git change set is available.");
        }
        int failures = state.failureCount(runId);
        if (failures == 0) {
            return AnalysisStageResult.skipped(
                    AnalysisStage.CHANGE_RELEVANCE,
                    AnalysisStageStatus.SKIPPED_NO_DATA,
                    "No extracted failures are available for change analysis.");
        }
        if (!recompute && state.currentRelevanceCount(runId) >= failures) {
            return AnalysisStageResult.reused(
                    AnalysisStage.CHANGE_RELEVANCE,
                    "Existing change-relevance-v1 results reused.");
        }
        return execute(
                AnalysisStage.CHANGE_RELEVANCE,
                () -> relevance.analyze(runId),
                "Change relevance analyzed.");
    }

    private AnalysisStageResult triageStage(UUID runId, boolean recompute) {
        if (!recompute && state.hasCurrentTriage(runId)) {
            return AnalysisStageResult.reused(
                    AnalysisStage.TRIAGE, "Existing triage-v1 result reused.");
        }
        return execute(
                AnalysisStage.TRIAGE,
                () -> triage.compute(runId),
                "Deterministic pipeline triage computed and persisted.");
    }

    private AnalysisStageResult execute(AnalysisStage stage, Operation operation, String message) {
        AnalysisStageResult result;
        try {
            operation.run();
            result = AnalysisStageResult.completed(stage, message);
        } catch (RuntimeException exception) {
            LOG.warn(
                    "pipeline_analysis_stage_failed stage={} errorType={} message={}",
                    stage,
                    exception.getClass().getSimpleName(),
                    safeMessage(exception));
            result = AnalysisStageResult.failed(
                    stage, safeMessage(exception), errorCode(exception));
        }
        return result;
    }

    private AnalysisStageResult measure(StageOperation operation) {
        Instant started = Instant.now();
        AnalysisStageResult result = operation.run();
        metrics.analysisStage(
                result.stage(), result.status(), Duration.between(started, Instant.now()));
        return result;
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException
                || exception instanceof GitHubIntegrationException
                || exception instanceof com.axiom.api.error.AnalysisPrerequisiteException
                || exception instanceof com.axiom.api.error.GitComparisonUnavailableException) {
            String message = exception.getMessage();
            return message == null ? "The stage failed." : message.substring(0, Math.min(500, message.length()));
        }
        return "The stage failed; inspect Axiom service diagnostics.";
    }

    private String errorCode(RuntimeException exception) {
        return exception.getClass().getSimpleName();
    }

    @FunctionalInterface
    private interface Operation {
        Object run();
    }

    @FunctionalInterface
    private interface StageOperation {
        AnalysisStageResult run();
    }
}
