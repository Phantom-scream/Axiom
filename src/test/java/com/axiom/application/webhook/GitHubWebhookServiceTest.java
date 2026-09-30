package com.axiom.application.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiom.api.error.WebhookPayloadTooLargeException;
import com.axiom.config.GitHubProperties;
import com.axiom.config.WebhookProperties;
import com.axiom.observability.AxiomMetrics;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitHubWebhookServiceTest {
    private static final String SECRET = "webhook-secret";
    private final GitHubWebhookDeliveryService deliveries =
            mock(GitHubWebhookDeliveryService.class);
    private final GitHubWorkflowAutomationService automation =
            mock(GitHubWorkflowAutomationService.class);
    private GitHubWebhookService service;

    @BeforeEach
    void setUp() {
        GitHubProperties github = new GitHubProperties(
                null, null, SECRET, false, false, null, null, null, 1, null, null);
        service = new GitHubWebhookService(
                new WebhookProperties(true, 2048, 1, 1, 10),
                new GitHubWebhookSignatureVerifier(github),
                deliveries,
                automation,
                JsonMapper.builder().findAndAddModules().build(),
                Runnable::run,
                new AxiomMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void acceptsCompletedWorkflowAndSchedulesAutomation() throws Exception {
        byte[] body = completedPayload();
        when(deliveries.accept(any(), any(), any(), any(), any())).thenReturn(true);

        var receipt = service.receive("workflow_run", "delivery-1", sign(body), body);

        assertThat(receipt.status()).isEqualTo("ACCEPTED");
        assertThat(receipt.duplicate()).isFalse();
        verify(automation).process(any());
    }

    @Test
    void duplicateAndUnsupportedDeliveriesAreIdempotent() throws Exception {
        byte[] body = completedPayload();
        when(deliveries.accept(any(), any(), any(), any(), any())).thenReturn(false);

        assertThat(service.receive("workflow_run", "delivery-2", sign(body), body).duplicate())
                .isTrue();
        verify(automation, never()).process(any());

        byte[] unsupported = "{}".getBytes(StandardCharsets.UTF_8);
        when(deliveries.accept("delivery-3", "ping", null, null, "IGNORED"))
                .thenReturn(true);
        assertThat(service.receive("ping", "delivery-3", sign(unsupported), unsupported).status())
                .isEqualTo("IGNORED");
    }

    @Test
    void ignoresNonCompletedWorkflowAndRejectsMalformedOrOversizedPayload() throws Exception {
        byte[] requested = completedPayload().clone();
        requested = new String(requested, StandardCharsets.UTF_8)
                .replace("completed", "requested")
                .getBytes(StandardCharsets.UTF_8);
        when(deliveries.accept(any(), any(), any(), any(), any())).thenReturn(true);

        assertThat(service.receive("workflow_run", "delivery-4", sign(requested), requested).status())
                .isEqualTo("IGNORED");
        verify(automation, never()).process(any());

        byte[] malformed = "{".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.receive(
                        "workflow_run", "delivery-5", sign(malformed), malformed))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] oversized = new byte[2049];
        assertThatThrownBy(() -> service.receive(
                        "workflow_run", "delivery-6", sign(oversized), oversized))
                .isInstanceOf(WebhookPayloadTooLargeException.class);
    }

    private byte[] completedPayload() {
        return """
                {"action":"completed","workflow_run":{"id":123,"pull_requests":[{"number":7}]},
                 "repository":{"name":"Axiom","owner":{"login":"Phantom-scream"}}}
                """
                .getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void queueRejectionIsPersistedAndDatabaseFailureDoesNotScheduleWork() throws Exception {
        var github = new GitHubProperties(null, null, SECRET, false, false, null, null, null, 1, null, null);
        var rejecting = new GitHubWebhookService(new WebhookProperties(true, 2048, 1, 1, 10),
                new GitHubWebhookSignatureVerifier(github), deliveries, automation,
                JsonMapper.builder().findAndAddModules().build(),
                task -> { throw new java.util.concurrent.RejectedExecutionException(); },
                new AxiomMetrics(new SimpleMeterRegistry()));
        when(deliveries.accept(any(), any(), any(), any(), any())).thenReturn(true);
        byte[] body = completedPayload();
        assertThatThrownBy(() -> rejecting.receive("workflow_run", "full-queue", sign(body), body))
                .isInstanceOf(com.axiom.integrations.github.exception.ExternalProviderUnavailableException.class);
        verify(deliveries).complete("full-queue", "FAILED", "WEBHOOK_QUEUE_FULL", "Webhook processing capacity is currently exhausted.");
        when(deliveries.accept(any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(() -> service.receive("workflow_run", "database-down", sign(body), body))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        verify(automation, never()).process(any());
    }

    private String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
