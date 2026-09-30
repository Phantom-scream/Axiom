package com.axiom.application.webhook;

import com.axiom.api.error.AnalysisPrerequisiteException;
import com.axiom.api.error.WebhookPayloadTooLargeException;
import com.axiom.config.WebhookProperties;
import com.axiom.integrations.github.dto.GitHubWorkflowRunWebhookDto;
import com.axiom.observability.AxiomMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import com.axiom.integrations.github.exception.ExternalProviderUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GitHubWebhookService {
    private final WebhookProperties properties;
    private final GitHubWebhookSignatureVerifier signatures;
    private final GitHubWebhookDeliveryService deliveries;
    private final GitHubWorkflowAutomationService automation;
    private final ObjectMapper objectMapper;
    private final Executor executor;
    private final AxiomMetrics metrics;

    public GitHubWebhookService(
            WebhookProperties properties,
            GitHubWebhookSignatureVerifier signatures,
            GitHubWebhookDeliveryService deliveries,
            GitHubWorkflowAutomationService automation,
            ObjectMapper objectMapper,
            @Qualifier("webhookExecutor") Executor executor,
            AxiomMetrics metrics) {
        this.properties = properties;
        this.signatures = signatures;
        this.deliveries = deliveries;
        this.automation = automation;
        this.objectMapper = objectMapper;
        this.executor = executor;
        this.metrics = metrics;
    }

    public Receipt receive(String event, String deliveryId, String signature, byte[] body) {
        if (!properties.enabledValue()) {
            throw new AnalysisPrerequisiteException("GitHub webhook processing is disabled.");
        }
        metrics.webhook("received");
        if (body.length > properties.effectiveMaxPayloadBytes()) {
            metrics.webhook("failed");
            throw new WebhookPayloadTooLargeException(properties.effectiveMaxPayloadBytes());
        }
        try {
            signatures.verify(body, signature);
        } catch (com.axiom.api.error.WebhookAuthenticationException exception) {
            metrics.webhook("failed");
            throw exception;
        }
        validateHeader(event, "X-GitHub-Event");
        validateHeader(deliveryId, "X-GitHub-Delivery");

        if (!"workflow_run".equals(event)) {
            boolean inserted = deliveries.accept(deliveryId, event, null, null, "IGNORED");
            if (!inserted) {
                metrics.webhook("duplicate");
                return new Receipt(deliveryId, event, "DUPLICATE", true);
            }
            metrics.webhook("accepted");
            return new Receipt(deliveryId, event, "IGNORED", false);
        }

        GitHubWorkflowRunWebhookDto payload = parse(body);
        validateWorkflowPayload(payload);
        String fullName = payload.repository().owner().login() + "/" + payload.repository().name();
        if (!"completed".equals(payload.action())) {
            boolean inserted = deliveries.accept(
                    deliveryId,
                    event,
                    fullName,
                    payload.workflowRun().id(),
                    "IGNORED");
            if (!inserted) {
                metrics.webhook("duplicate");
                return new Receipt(deliveryId, event, "DUPLICATE", true);
            }
            metrics.webhook("accepted");
            return new Receipt(deliveryId, event, "IGNORED", false);
        }

        var command = new GitHubWorkflowAutomationService.WorkflowRunCommand(deliveryId,
                payload.repository().owner().login(),payload.repository().name(),payload.workflowRun().id(),
                Math.max(1,payload.workflowRun().runAttempt()),payload.workflowRun().pullRequests()!=null && !payload.workflowRun().pullRequests().isEmpty());
        boolean inserted = deliveries.acceptWorkflow(command);
        if (!inserted) {
            metrics.webhook("duplicate");
            return new Receipt(deliveryId, event, "DUPLICATE", true);
        }
        try {
            executor.execute(() -> automation.process(command));
        } catch (RejectedExecutionException exception) {
            // Already durably accepted: the recovery scheduler will submit it later.
            metrics.webhook("recovered");
        }
        metrics.webhook("accepted");
        return new Receipt(deliveryId, event, "ACCEPTED", false);
    }

    private GitHubWorkflowRunWebhookDto parse(byte[] body) {
        try {
            return objectMapper.readValue(body, GitHubWorkflowRunWebhookDto.class);
        } catch (IOException exception) {
            metrics.webhook("failed");
            throw new IllegalArgumentException("Malformed GitHub workflow_run payload.");
        }
    }

    private void validateWorkflowPayload(GitHubWorkflowRunWebhookDto payload) {
        if (payload == null
                || payload.action() == null
                || payload.workflowRun() == null
                || payload.workflowRun().id() <= 0
                || payload.repository() == null
                || payload.repository().owner() == null
                || blank(payload.repository().owner().login())
                || blank(payload.repository().name())) {
            throw new IllegalArgumentException("Malformed GitHub workflow_run payload.");
        }
        if (!payload.repository().owner().login().matches("[A-Za-z0-9-]{1,100}")
                || !payload.repository().name().matches("[A-Za-z0-9_.-]{1,100}")) {
            throw new IllegalArgumentException("GitHub repository identity is invalid.");
        }
    }

    private void validateHeader(String value, String name) {
        if (blank(value) || value.length() > 128 || !value.matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException(name + " is missing or invalid.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Receipt(String deliveryId, String event, String status, boolean duplicate) {}
}
