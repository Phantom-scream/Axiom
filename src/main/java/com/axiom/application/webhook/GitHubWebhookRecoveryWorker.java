package com.axiom.application.webhook;

import com.axiom.config.WebhookProperties;
import com.axiom.observability.AxiomMetrics;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GitHubWebhookRecoveryWorker {
    private final GitHubWebhookDeliveryService deliveries;
    private final GitHubWorkflowAutomationService automation;
    private final WebhookProperties properties;
    private final Executor executor;
    private final AxiomMetrics metrics;
    public GitHubWebhookRecoveryWorker(GitHubWebhookDeliveryService deliveries, GitHubWorkflowAutomationService automation,
            WebhookProperties properties, @Qualifier("webhookExecutor") Executor executor, AxiomMetrics metrics) {
        this.deliveries=deliveries; this.automation=automation; this.properties=properties; this.executor=executor; this.metrics=metrics;
    }
    @Scheduled(fixedDelayString="${axiom.webhook-recovery.poll-milliseconds:10000}", initialDelayString="${axiom.webhook-recovery.poll-milliseconds:10000}")
    public void recover() {
        if (!properties.enabledValue()) return;
        try {
            for (var command : deliveries.recoverable()) {
                try { executor.execute(() -> automation.process(command)); metrics.webhook("recovered"); }
                catch (RejectedExecutionException saturated) { break; }
            }
        } catch (org.springframework.dao.DataAccessException unavailable) {
            metrics.webhook("failed");
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("webhook_recovery_scan_failed errorType={}", unavailable.getClass().getSimpleName());
        }
    }
    @Configuration @EnableScheduling static class SchedulingConfiguration {}
}
