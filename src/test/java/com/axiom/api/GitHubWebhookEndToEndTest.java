package com.axiom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Real application services and PostgreSQL; only the remote GitHub boundary is mocked. */
@AutoConfigureMockMvc
class GitHubWebhookEndToEndTest extends IntegrationTestSupport {
    private static final AtomicInteger creates = new AtomicInteger();
    private static final AtomicInteger updates = new AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicBoolean failWrites = new java.util.concurrent.atomic.AtomicBoolean();
    private static final HttpServer github = startGitHub();
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("axiom.github.base-url", () -> "http://localhost:" + github.getAddress().getPort());
        registry.add("axiom.github.token", () -> "mock-only-token");
        registry.add("axiom.webhook.enabled", () -> true);
        registry.add("axiom.github.webhook-secret", () -> "mock-only-secret");
        registry.add("axiom.github.auto-publish-check", () -> true);
        registry.add("axiom.github.auto-publish-pr-comment", () -> true);
    }

    @AfterAll
    void stopServer() { github.stop(0); }

    @Test
    void signedWebhookIngestsExactAttemptAnalyzesAndPublishesWithoutDuplicates() throws Exception {
        deliver("full-flow-first");
        awaitCompleted("full-flow-first");
        assertThat(count("pipeline_runs", "external_run_id=887766 and attempt=2")).isEqualTo(1);
        assertThat(count("failure_events", "pipeline_run_id in (select id from pipeline_runs where external_run_id=887766)")).isPositive();
        assertThat(count("failure_diagnoses", "failure_event_id in (select id from failure_events where pipeline_run_id in (select id from pipeline_runs where external_run_id=887766))")).isPositive();
        assertThat(count("pipeline_triage_results", "pipeline_run_id in (select id from pipeline_runs where external_run_id=887766)")).isEqualTo(1);
        assertThat(count("change_relevance_results", "pipeline_run_id in (select id from pipeline_runs where external_run_id=887766)")).isPositive();
        assertThat(count("github_publications", "pipeline_run_id in (select id from pipeline_runs where external_run_id=887766)")).isEqualTo(2);
        assertThat(creates.get()).isEqualTo(2);

        deliver("full-flow-first");
        assertThat(creates.get()).isEqualTo(2);
        deliver("full-flow-second");
        awaitCompleted("full-flow-second");
        assertThat(creates.get()).isEqualTo(2);
        assertThat(updates.get()).isEqualTo(2);
        assertThat(count("pipeline_triage_results", "pipeline_run_id in (select id from pipeline_runs where external_run_id=887766)")).isEqualTo(1);
        failWrites.set(true);
        deliver("full-flow-publication-failure");
        awaitOutcome("full-flow-publication-failure", "COMPLETED_WITH_PUBLICATION_FAILURE");
        assertThat(count("pipeline_triage_results", "pipeline_run_id in (select id from pipeline_runs where external_run_id=887766)")).isEqualTo(1);
    }

    private int count(String table, String where) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + where, Integer.class);
    }

    private void awaitCompleted(String delivery) throws Exception {
        awaitOutcome(delivery, "COMPLETED");
    }

    private void awaitOutcome(String delivery, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        String state;
        do {
            state = jdbc.queryForObject("select status from github_webhook_deliveries where delivery_id=?", String.class, delivery);
            if (state.startsWith("COMPLETED") || state.equals("FAILED")) break;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertThat(state).as("delivery %s processing outcome", delivery).isEqualTo(expected);
    }

    private void deliver(String id) throws Exception {
        byte[] body = ("{\"action\":\"completed\",\"workflow_run\":{\"id\":887766,\"run_attempt\":2,\"head_sha\":\"head123\",\"pull_requests\":[{\"number\":7}]},\"repository\":{\"name\":\"repo\",\"owner\":{\"login\":\"automation-owner\"}}}").getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("mock-only-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mvc.perform(post("/api/v1/webhooks/github").header("X-GitHub-Event", "workflow_run")
                .header("X-GitHub-Delivery", id).header("X-Hub-Signature-256", "sha256=" + HexFormat.of().formatHex(mac.doFinal(body)))
                .contentType("application/json").content(body)).andExpect(status().isAccepted());
    }

    private static HttpServer startGitHub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String response;
                int status = 200;
                String contentType = "application/json";
                if (path.endsWith("/attempts/2")) {
                    response = """
                        {"id":887766,"status":"completed","conclusion":"failure","event":"pull_request","head_sha":"head123","head_branch":"feature","run_attempt":2,"created_at":"2026-09-29T10:00:00Z","updated_at":"2026-09-29T10:02:00Z","pull_requests":[{"number":7,"base":{"sha":"base123"},"head":{"sha":"head123"}}]}
                        """;
                } else if (path.endsWith("/attempts/2/jobs")) {
                    response = "{\"total_count\":0,\"jobs\":[]}";
                } else if (path.endsWith("/attempts/2/logs")) {
                    response = "java.net.ConnectException: Connection refused\nBUILD FAILED\n";
                    contentType = "text/plain";
                } else if (path.endsWith("/compare/base123...head123")) {
                    response = "{\"status\":\"ahead\",\"ahead_by\":1,\"behind_by\":0,\"base_commit\":{\"sha\":\"base123\"},\"files\":[{\"filename\":\"README.md\",\"status\":\"modified\",\"additions\":1,\"deletions\":0,\"changes\":1}]}";
                } else if (path.contains("/check-runs") || path.contains("/issues/")) {
                    if (exchange.getRequestMethod().equals("POST")) creates.incrementAndGet();
                    else if (exchange.getRequestMethod().equals("PATCH")) updates.incrementAndGet();
                    exchange.getRequestBody().readAllBytes();
                    response = "{\"id\":4242,\"html_url\":\"https://github.com/mock/report\"}";
                    if (failWrites.get()) status = 403;
                } else {
                    status = 404;
                    response = "{}";
                }
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
