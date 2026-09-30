package com.axiom.application.webhook;

import com.axiom.api.error.AnalysisPrerequisiteException;
import com.axiom.application.analysis.PipelineAnalysisOrchestrator;
import com.axiom.application.pipeline.PipelineIngestionService;
import com.axiom.application.pipeline.PipelineRunReference;
import com.axiom.application.publication.GitHubPrTriagePublisher;
import com.axiom.application.publication.GitHubTriageCheckPublisher;
import com.axiom.config.GitHubProperties;
import com.axiom.domain.analysis.AnalysisStageStatus;
import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.observability.AxiomMetrics;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class GitHubWorkflowAutomationService {
    private static final Logger LOG = LoggerFactory.getLogger(GitHubWorkflowAutomationService.class);
    private final PipelineIngestionService ingestion;
    private final PipelineAnalysisOrchestrator orchestrator;
    private final GitHubTriageCheckPublisher checkPublisher;
    private final GitHubPrTriagePublisher prPublisher;
    private final GitHubWebhookDeliveryService deliveries;
    private final GitHubProperties properties;
    private final AxiomMetrics metrics;

    public GitHubWorkflowAutomationService(
            PipelineIngestionService ingestion,
            PipelineAnalysisOrchestrator orchestrator,
            GitHubTriageCheckPublisher checkPublisher,
            GitHubPrTriagePublisher prPublisher,
            GitHubWebhookDeliveryService deliveries,
            GitHubProperties properties,
            AxiomMetrics metrics) {
        this.ingestion = ingestion;
        this.orchestrator = orchestrator;
        this.checkPublisher = checkPublisher;
        this.prPublisher = prPublisher;
        this.deliveries = deliveries;
        this.properties = properties;
        this.metrics = metrics;
    }

    public void process(WorkflowRunCommand command) {
        deliveries.runClaimed(command, () -> processClaimed(command));
    }

    private void processClaimed(WorkflowRunCommand command) {
        Instant started = Instant.now();
        MDC.put("webhookDeliveryId", command.deliveryId());
        try {
            java.util.UUID runId = deliveries.persistedRun(command.deliveryId()).orElseGet(() -> ingestion.ingest(new PipelineRunReference(
                    CiProviderType.GITHUB_ACTIONS,
                    command.owner(),
                    command.repository(),
                    command.externalRunId(), command.runAttempt())).id());
            deliveries.pipelineRun(command.deliveryId(), runId);
            MDC.put("pipelineRunId", runId.toString());
            var analysis = orchestrator.analyze(runId, false);
            boolean partialFailure = analysis.stages().stream()
                    .anyMatch(stage -> stage.status() == AnalysisStageStatus.FAILED);
            var publication = analysis.triageAvailable() ? publish(runId) : new PublicationOutcome(null,false,false);
            String publicationError = publication.error();
            String status = publicationError != null
                    ? "COMPLETED_WITH_PUBLICATION_FAILURE"
                    : partialFailure ? "COMPLETED_WITH_STAGE_FAILURE" : "COMPLETED";
            deliveries.finish(
                    command.deliveryId(),
                    status,
                    publicationError != null ? "PUBLICATION_FAILED" : partialFailure ? "ANALYSIS_STAGE_FAILED" : null,
                    publicationError != null ? publicationError : partialFailure ? "One or more analysis stages failed; inspect stage results." : null,
                    !publication.permanentFailure() && (partialFailure || publication.retryable()));
            if (partialFailure || publicationError != null) metrics.webhook("failed");
            metrics.webhookDuration(status.toLowerCase(), Duration.between(started, Instant.now()));
            LOG.info(
                    "github_webhook_processing_completed deliveryId={} pipelineRunId={} status={}",
                    command.deliveryId(),
                    runId,
                    status);
        } catch (RuntimeException exception) {
            deliveries.finish(
                    command.deliveryId(),
                    "FAILED",
                    exception.getClass().getSimpleName(),
                    safeMessage(exception), retryable(exception));
            metrics.webhook("failed");
            metrics.webhookDuration("failed", Duration.between(started, Instant.now()));
            LOG.warn(
                    "github_webhook_processing_failed deliveryId={} errorType={} message={}",
                    command.deliveryId(),
                    exception.getClass().getSimpleName(),
                    safeMessage(exception));
        } finally {
            MDC.remove("webhookDeliveryId");
            MDC.remove("pipelineRunId");
        }
    }

    private boolean retryable(RuntimeException exception) {
        return exception instanceof com.axiom.integrations.github.exception.GitHubRateLimitException
                || exception instanceof org.springframework.dao.TransientDataAccessException
                || exception instanceof org.springframework.dao.DataAccessResourceFailureException
                || (exception instanceof com.axiom.integrations.github.exception.ExternalProviderUnavailableException unavailable && unavailable.retryable());
    }

    private PublicationOutcome publish(java.util.UUID pipelineRunId) {
        String error = null;
        boolean retry = false;
        boolean permanentFailure = false;
        if (properties.autoPublishCheckEnabled()) {
            try {
                checkPublisher.publish(pipelineRunId);
            } catch (RuntimeException exception) {
                error = "GitHub Check: " + safeMessage(exception);
                retry = retryable(exception);
                permanentFailure = !retry;
            }
        }
        if (properties.autoPublishPrCommentEnabled()) {
            try {
                prPublisher.publish(pipelineRunId);
            } catch (AnalysisPrerequisiteException exception) {
                LOG.info("github_pr_publication_skipped pipelineRunId={} reason=no_pull_request", pipelineRunId);
            } catch (RuntimeException exception) {
                error = append(error, "PR comment: " + safeMessage(exception));
                retry = retry || retryable(exception);
                permanentFailure = permanentFailure || !retryable(exception);
            }
        }
        return new PublicationOutcome(error,retry,permanentFailure);
    }

    private record PublicationOutcome(String error,boolean retryable,boolean permanentFailure) {}

    private String append(String current, String next) {
        return current == null ? next : current + "; " + next;
    }

    private String safeMessage(RuntimeException exception) {
        if (exception instanceof com.axiom.integrations.github.exception.GitHubIntegrationException) {
            return exception.getClass().getSimpleName();
        }
        return "Processing failed; inspect Axiom diagnostics (" + exception.getClass().getSimpleName() + ").";
    }

    public record WorkflowRunCommand(
            String deliveryId,
            String owner,
            String repository,
            long externalRunId,
            int runAttempt,
            boolean hasPullRequest) {
        public WorkflowRunCommand(String deliveryId, String owner, String repository, long externalRunId, boolean hasPullRequest) {
            this(deliveryId, owner, repository, externalRunId, 1, hasPullRequest);
        }
    }
}
