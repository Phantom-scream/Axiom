package com.axiom.integrations.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.axiom.config.GitHubProperties;
import com.axiom.integrations.github.client.GitHubApiClient;
import com.axiom.integrations.github.exception.GitHubAuthenticationException;
import com.axiom.integrations.github.exception.ExternalProviderUnavailableException;
import com.axiom.observability.AxiomMetrics;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class GitHubApiReliabilityTest {
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    @Test
    void retriesTemporaryServiceFailureThenSucceeds() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/repos/o/r/actions/runs/7", exchange -> {
            int status = requests.incrementAndGet() < 2 ? 503 : 200;
            byte[] body = (status == 200
                            ? "{\"id\":7,\"status\":\"completed\",\"conclusion\":\"success\",\"head_sha\":\"abc\",\"run_attempt\":1}"
                            : "{}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var client = new GitHubApiClient(
                WebClient.builder(), properties(3), new AxiomMetrics(new SimpleMeterRegistry()));
        assertThat(client.workflowRun("o", "r", 7).id()).isEqualTo(7);
        assertThat(requests).hasValue(2);
    }

    @Test
    void authenticationFailureIsNotRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/repos/o/r/actions/runs/7", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        var client = new GitHubApiClient(
                WebClient.builder(), properties(3), new AxiomMetrics(new SimpleMeterRegistry()));
        assertThatThrownBy(() -> client.workflowRun("o", "r", 7))
                .isInstanceOf(GitHubAuthenticationException.class);
        assertThat(requests).hasValue(1);
    }

    @Test
    void retryExhaustionIsBounded() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/repos/o/r/actions/runs/7", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        var client = new GitHubApiClient(
                WebClient.builder(), properties(3), new AxiomMetrics(new SimpleMeterRegistry()));
        assertThatThrownBy(() -> client.workflowRun("o", "r", 7))
                .isInstanceOf(ExternalProviderUnavailableException.class);
        assertThat(requests).hasValue(3);
    }

    private GitHubProperties properties(int attempts) {
        return new GitHubProperties(
                "token",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                null,
                false,
                false,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                attempts,
                Duration.ofMillis(1),
                Duration.ofMillis(2));
    }

    @Test
    void ordinaryPermissionDenialIsNeverRetried() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        var client = new GitHubApiClient(WebClient.builder(), properties(3));
        assertThatThrownBy(() -> client.workflowRun("o", "r", 7)).isInstanceOf(com.axiom.integrations.github.exception.GitHubPermissionException.class);
        assertThat(requests).hasValue(1);
    }

    @Test
    void requestTimeoutIsTranslatedWithoutExposingProviderContent() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try { Thread.sleep(200); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        var settings = new GitHubProperties("mock-token", "http://localhost:" + server.getAddress().getPort(),
                null, false, false, Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofMillis(30), 1, Duration.ofMillis(1), Duration.ofMillis(2));
        var client = new GitHubApiClient(WebClient.builder(), settings);
        assertThatThrownBy(() -> client.workflowRun("o", "r", 7))
                .isInstanceOf(ExternalProviderUnavailableException.class)
                .hasMessageNotContaining("mock-token");
    }

    @Test
    void honorsShortRetryAfterAndDoesNotRetryUnsafeCreates() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            int n = requests.incrementAndGet();
            if (n == 1) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(429, -1);
            } else {
                byte[] bytes = "{\"id\":7,\"status\":\"completed\",\"conclusion\":\"success\",\"head_sha\":\"abc\",\"run_attempt\":1}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        var registry = new SimpleMeterRegistry();
        var client = new GitHubApiClient(WebClient.builder(), properties(3), new AxiomMetrics(registry));
        assertThat(client.workflowRun("o", "r", 7).id()).isEqualTo(7);
        assertThat(requests).hasValue(2);
        assertThat(registry.get("axiom.github.rate_limits").counter().count()).isEqualTo(1);
        requests.set(0);
        assertThatThrownBy(() -> client.createIssueComment("o", "r", 1,
                new com.axiom.integrations.github.dto.GitHubIssueCommentRequestDto("safe body")))
                .isInstanceOf(com.axiom.integrations.github.exception.GitHubRateLimitException.class);
        assertThat(requests).hasValue(1);
    }
}
