package com.axiom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.analysis.PipelineAnalysisOrchestrator;
import com.axiom.application.pipeline.PersistedPipelineRun;
import com.axiom.application.pipeline.PipelineIngestionService;
import com.axiom.domain.analysis.PipelineAnalysisResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "axiom.webhook.enabled=true",
    "axiom.github.webhook-secret=integration-secret",
    "axiom.github.auto-publish-check=false",
    "axiom.github.auto-publish-pr-comment=false"
})
class GitHubWebhookControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MeterRegistry meters;
    @MockitoBean private PipelineIngestionService ingestion;
    @MockitoBean private PipelineAnalysisOrchestrator orchestrator;

    @Test
    void signedCompletedWorkflowTriggersIngestionAndAnalysisOnce() throws Exception {
        UUID runId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        jdbc.update(
                "insert into repositories(id,provider,owner,name) values(?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "Phantom-scream",
                "Axiom");
        jdbc.update(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,status,conclusion,attempt)
                values(?,?,?,?,?,?,?)
                """,
                runId,
                repositoryId,
                998L,
                "abc",
                "COMPLETED",
                "SUCCESS",
                2);
        when(ingestion.ingest(any()))
                .thenReturn(new PersistedPipelineRun(runId, 2, 1, 1, Instant.now()));
        when(orchestrator.analyze(runId, false))
                .thenReturn(new PipelineAnalysisResult(runId, false, List.of(), true));
        byte[] body = payload("completed");

        perform("delivery-automation", body)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.duplicate").value(false));
        verify(ingestion, timeout(2_000)).ingest(any());
        verify(orchestrator, timeout(2_000)).analyze(runId, false);

        perform("delivery-automation", body)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.duplicate").value(true));
        Thread.sleep(100);
        org.mockito.Mockito.verifyNoMoreInteractions(ingestion);
        assertThat(jdbc.queryForObject(
                        "select count(*) from github_webhook_deliveries where delivery_id='delivery-automation'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(meters.get("axiom.webhook.accepted").counter().count()).isPositive();
        assertThat(meters.get("axiom.webhook.duplicate").counter().count()).isPositive();
    }

    @Test
    void invalidMissingOrModifiedSignaturesAreRejected() throws Exception {
        byte[] body = payload("completed");
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", "bad-signature")
                        .header("X-Hub-Signature-256", "sha256=00")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", "missing-signature")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());

        byte[] original = payload("completed");
        byte[] modified = payload("requested");
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", "modified-payload")
                        .header("X-Hub-Signature-256", sign(original))
                        .contentType("application/json")
                        .content(modified))
                .andExpect(status().isUnauthorized());
        verify(ingestion, never()).ingest(any());
    }

    @Test
    void nonCompletedAndUnsupportedEventsAreIgnored() throws Exception {
        byte[] requested = payload("requested");
        perform("delivery-requested", requested)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("IGNORED"));

        byte[] ping = "{}".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/webhooks/github")
                        .header("X-GitHub-Event", "ping")
                        .header("X-GitHub-Delivery", "delivery-ping")
                        .header("X-Hub-Signature-256", sign(ping))
                        .contentType("application/json")
                        .content(ping))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("IGNORED"));
        verify(ingestion, never()).ingest(any());
    }

    private org.springframework.test.web.servlet.ResultActions perform(String delivery, byte[] body)
            throws Exception {
        return mockMvc.perform(post("/api/v1/webhooks/github")
                .header("X-GitHub-Event", "workflow_run")
                .header("X-GitHub-Delivery", delivery)
                .header("X-Hub-Signature-256", sign(body))
                .contentType("application/json")
                .content(body));
    }

    private byte[] payload(String action) {
        return ("{\"action\":\"" + action
                        + "\",\"workflow_run\":{\"id\":998,\"run_attempt\":2,\"head_sha\":\"abc\",\"pull_requests\":[]},"
                        + "\"repository\":{\"name\":\"Axiom\",\"owner\":{\"login\":\"Phantom-scream\"}}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                "integration-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
