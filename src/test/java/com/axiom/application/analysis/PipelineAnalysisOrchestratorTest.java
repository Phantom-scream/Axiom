package com.axiom.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.application.pipeline.GitChangeIngestionService;
import com.axiom.application.testreport.TestFailureCorrelationService;
import com.axiom.application.testreport.TestStabilitySnapshotService;
import com.axiom.domain.analysis.AnalysisStage;
import com.axiom.domain.analysis.AnalysisStageStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipelineAnalysisOrchestratorTest {
    private final PipelineAnalysisStateService state = mock(PipelineAnalysisStateService.class);
    private final PipelineLogAnalysisService logs = mock(PipelineLogAnalysisService.class);
    private final PipelineDiagnosisService diagnoses = mock(PipelineDiagnosisService.class);
    private final TestFailureCorrelationService correlations =
            mock(TestFailureCorrelationService.class);
    private final TestStabilitySnapshotService stability =
            mock(TestStabilitySnapshotService.class);
    private final GitChangeIngestionService changes = mock(GitChangeIngestionService.class);
    private final PipelineChangeAnalysisService relevance =
            mock(PipelineChangeAnalysisService.class);
    private final PipelineTriageApplicationService triage =
            mock(PipelineTriageApplicationService.class);
    private final PipelineAnalysisOrchestrator orchestrator = new PipelineAnalysisOrchestrator(
            state, logs, diagnoses, correlations, stability, changes, relevance, triage);
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void completeEvidenceAvailable() {
        when(state.hasStoredLog(runId)).thenReturn(true);
        when(state.failureCount(runId)).thenReturn(1);
        when(state.testExecutionCount(runId)).thenReturn(1);
        when(state.failedTestExecutionCount(runId)).thenReturn(1);
        when(state.stableTestCount(runId)).thenReturn(1);
        when(state.hasComparisonMetadata(runId)).thenReturn(true);
        when(state.hasChangeSet(runId)).thenReturn(true);
        when(state.hasCurrentTriage(runId)).thenReturn(true);
    }

    @Test
    void executesEveryAvailableStageInOrder() {
        var result = orchestrator.analyze(runId, true);

        assertThat(result.stages())
                .extracting("stage")
                .containsExactly((Object[]) AnalysisStage.values());
        assertThat(result.stages())
                .extracting("status")
                .containsOnly(AnalysisStageStatus.COMPLETED);
        verify(logs).process(runId);
        verify(diagnoses).diagnose(runId);
        verify(correlations).correlate(runId);
        verify(stability).computeForPipeline(runId);
        verify(changes).ingest(runId);
        verify(relevance).analyze(runId);
        verify(triage).compute(runId);
    }

    @Test
    void reusesEveryCurrentDerivedStageWhenRecomputeIsFalse() {
        when(state.currentDiagnosisCount(runId)).thenReturn(1);
        when(state.correlationsCurrent(runId)).thenReturn(true);
        when(state.stabilitySnapshotCount(runId)).thenReturn(1);
        when(state.currentRelevanceCount(runId)).thenReturn(1);

        var result = orchestrator.analyze(runId, false);

        assertThat(result.stages())
                .extracting("status")
                .containsOnly(AnalysisStageStatus.REUSED);
        verify(logs, never()).process(runId);
        verify(changes, never()).ingest(runId);
        verify(triage, never()).compute(runId);
    }

    @Test
    void skipsAbsentOptionalEvidenceButStillRunsTriage() {
        when(state.hasStoredLog(runId)).thenReturn(false);
        when(state.failureCount(runId)).thenReturn(0);
        when(state.testExecutionCount(runId)).thenReturn(0);
        when(state.stableTestCount(runId)).thenReturn(0);
        when(state.hasComparisonMetadata(runId)).thenReturn(false);
        when(state.hasChangeSet(runId)).thenReturn(false);
        when(state.hasCurrentTriage(runId)).thenReturn(false);

        var result = orchestrator.analyze(runId, false);

        assertThat(result.stages())
                .filteredOn(stage -> stage.stage() == AnalysisStage.TEST_CORRELATION)
                .singleElement()
                .extracting("status")
                .isEqualTo(AnalysisStageStatus.SKIPPED_NO_DATA);
        assertThat(result.stages())
                .filteredOn(stage -> stage.stage() == AnalysisStage.CHANGE_INGESTION)
                .singleElement()
                .extracting("status")
                .isEqualTo(AnalysisStageStatus.SKIPPED_NOT_APPLICABLE);
        assertThat(result.stages().getLast().status()).isEqualTo(AnalysisStageStatus.COMPLETED);
        verify(triage).compute(runId);
    }

    @Test
    void reportsStageFailureAndContinues() {
        when(logs.process(runId)).thenThrow(new IllegalArgumentException("invalid stored log"));

        var result = orchestrator.analyze(runId, true);

        assertThat(result.stages().getFirst().status()).isEqualTo(AnalysisStageStatus.FAILED);
        assertThat(result.stages().getFirst().message()).isEqualTo("invalid stored log");
        assertThat(result.stages().getFirst().errorCode())
                .isEqualTo("IllegalArgumentException");
        verify(triage).compute(runId);
    }

    @Test
    void missingPipelineIsNotConvertedIntoAStageFailure() {
        var missing = new ResourceNotFoundException("missing");
        org.mockito.Mockito.doThrow(missing).when(state).requireRun(runId);

        assertThatThrownBy(() -> orchestrator.analyze(runId, false)).isSameAs(missing);
        verify(triage, never()).compute(runId);
    }
}
