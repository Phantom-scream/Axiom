package com.axiom.application.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiom.api.error.AnalysisPrerequisiteException;
import com.axiom.application.analysis.PipelineAnalysisOrchestrator;
import com.axiom.application.pipeline.PersistedPipelineRun;
import com.axiom.application.pipeline.PipelineIngestionService;
import com.axiom.application.publication.GitHubPrTriagePublisher;
import com.axiom.application.publication.GitHubTriageCheckPublisher;
import com.axiom.config.GitHubProperties;
import com.axiom.domain.analysis.PipelineAnalysisResult;
import com.axiom.observability.AxiomMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitHubWorkflowAutomationServiceTest {
    private final PipelineIngestionService ingestion = mock(PipelineIngestionService.class);
    private final PipelineAnalysisOrchestrator orchestrator = mock(PipelineAnalysisOrchestrator.class);
    private final GitHubTriageCheckPublisher checks = mock(GitHubTriageCheckPublisher.class);
    private final GitHubPrTriagePublisher comments = mock(GitHubPrTriagePublisher.class);
    private final GitHubWebhookDeliveryService deliveries = mock(GitHubWebhookDeliveryService.class);
    private final UUID runId = UUID.randomUUID();

    @Test
    void completedWebhookRunsIngestionAndAnalysisWithoutPublishingByDefault() {
        service(false, false).process(command());

        verify(ingestion).ingest(any());
        verify(orchestrator).analyze(runId, false);
        verify(checks, never()).publish(any());
        verify(comments, never()).publish(any());
        verify(deliveries).finish("delivery", "COMPLETED", null, null, false);
    }

    @Test
    void enabledPublishersRunAndPrAbsenceIsSafelySkipped() {
        when(comments.publish(runId)).thenThrow(new AnalysisPrerequisiteException("no PR"));

        service(true, true).process(command());

        verify(checks).publish(runId);
        verify(comments).publish(runId);
        verify(deliveries).finish("delivery", "COMPLETED", null, null, false);
    }

    @Test
    void publicationFailureDoesNotEraseSuccessfulAnalysis() {
        when(checks.publish(runId)).thenThrow(new IllegalStateException("provider down"));

        service(true, false).process(command());

        verify(orchestrator).analyze(runId, false);
        verify(deliveries)
                .finish(
                        eq("delivery"),
                        eq("COMPLETED_WITH_PUBLICATION_FAILURE"),
                        eq("PUBLICATION_FAILED"),
                        any(), eq(false));
    }

    private GitHubWorkflowAutomationService service(boolean autoCheck, boolean autoComment) {
        org.mockito.Mockito.doAnswer(call -> { call.getArgument(1, Runnable.class).run(); return null; })
                .when(deliveries).runClaimed(any(), any());
        when(ingestion.ingest(any()))
                .thenReturn(new PersistedPipelineRun(runId, 1, 1, 1, Instant.now()));
        when(orchestrator.analyze(runId, false))
                .thenReturn(new PipelineAnalysisResult(runId, false, List.of(), true));
        var github = new GitHubProperties(
                "token",
                null,
                "secret",
                autoCheck,
                autoComment,
                null,
                null,
                null,
                1,
                null,
                null);
        return new GitHubWorkflowAutomationService(
                ingestion,
                orchestrator,
                checks,
                comments,
                deliveries,
                github,
                new AxiomMetrics(new SimpleMeterRegistry()));
    }

    @Test void ambiguousCreateIsNotRepeatedWhenAnotherPublisherHasTransientFailure() {
        when(checks.publish(runId)).thenThrow(new com.axiom.integrations.github.exception.GitHubPublicationOutcomeUnknownException());
        when(comments.publish(runId)).thenThrow(new com.axiom.integrations.github.exception.ExternalProviderUnavailableException());
        service(true,true).process(command());
        verify(deliveries).finish(eq("delivery"),eq("COMPLETED_WITH_PUBLICATION_FAILURE"),eq("PUBLICATION_FAILED"),any(),eq(false));
        verify(orchestrator).analyze(runId,false);
    }

    @Test
    void providerFailureIsRecordedWithoutCallingAnalysisOrLeakingMessages() {
        var service = service(false, false);
        when(ingestion.ingest(any())).thenThrow(new com.axiom.integrations.github.exception.GitHubRateLimitException());
        service.process(command());
        verify(orchestrator, never()).analyze(any(), eq(false));
        verify(deliveries).finish("delivery", "FAILED", "GitHubRateLimitException", "GitHubRateLimitException", true);
    }

    @Test
    void partialAnalysisFailureIsExplicitAndDoesNotPublishMissingTriage() {
        var service = service(true, true);
        when(orchestrator.analyze(runId, false)).thenReturn(new PipelineAnalysisResult(runId, false,
                List.of(com.axiom.domain.analysis.AnalysisStageResult.failed(
                        com.axiom.domain.analysis.AnalysisStage.TRIAGE, "safe error", "TRIAGE_FAILED")), false));
        service.process(command());
        verify(deliveries).finish(eq("delivery"), eq("COMPLETED_WITH_STAGE_FAILURE"), eq("ANALYSIS_STAGE_FAILED"), any(), eq(true));
        verify(checks, never()).publish(any());
        verify(comments, never()).publish(any());
    }

    private GitHubWorkflowAutomationService.WorkflowRunCommand command() {
        return new GitHubWorkflowAutomationService.WorkflowRunCommand(
                "delivery", "owner", "repo", 123, true);
    }
}
