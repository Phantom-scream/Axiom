package com.axiom.integrations.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.axiom.config.GitHubProperties;
import com.axiom.integrations.github.client.GitHubApiClient;
import com.axiom.integrations.github.client.GitHubPullRequestClient;
import com.axiom.integrations.github.exception.ExternalProviderUnavailableException;
import com.axiom.integrations.github.exception.GitHubAuthenticationException;
import com.axiom.integrations.github.exception.GitHubPermissionException;
import com.axiom.integrations.github.exception.GitHubPullRequestNotFoundException;
import com.axiom.integrations.github.exception.GitHubRateLimitException;
import com.axiom.integrations.github.exception.InvalidGitHubCommentException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.reactive.function.client.WebClient;

class GitHubPullRequestClientHttpTest {
    private HttpServer server;
    private final AtomicReference<Response> response = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> accept = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void createsMarkedCommentOnTheExpectedPullRequest() {
        response.set(new Response(
                201,
                "{\"id\":456,\"html_url\":\"https://github.test/comments/456\"}",
                Map.of()));

        var result = client().createComment(
                "owner", "repo", 42, "<!-- axiom-ci-intelligence -->\n## Axiom CI Intelligence");

        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/repos/owner/repo/issues/42/comments");
        assertThat(authorization.get()).isEqualTo("Bearer test-token");
        assertThat(accept.get()).contains("application/vnd.github+json");
        assertThat(body.get()).contains("axiom-ci-intelligence", "Axiom CI Intelligence");
        assertThat(result.id()).isEqualTo(456);
    }

    @Test
    void updatesTheTrackedComment() {
        response.set(new Response(
                200,
                "{\"id\":456,\"html_url\":\"https://github.test/comments/456\"}",
                Map.of()));

        client().updateComment("owner", "repo", 456, "updated report");

        assertThat(method.get()).isEqualTo("PATCH");
        assertThat(path.get()).isEqualTo("/repos/owner/repo/issues/comments/456");
        assertThat(body.get()).contains("updated report");
    }

    @ParameterizedTest
    @MethodSource("errors")
    void translatesCommentErrors(
            int status,
            Map<String, String> headers,
            Class<? extends RuntimeException> expected) {
        response.set(new Response(status, "{\"message\":\"failure\"}", headers));

        assertThatThrownBy(() -> client().createComment("owner", "repo", 42, "body"))
                .isInstanceOf(expected);
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(401, Map.of(), GitHubAuthenticationException.class),
                Arguments.of(403, Map.of(), GitHubPermissionException.class),
                Arguments.of(
                        403,
                        Map.of("X-RateLimit-Remaining", "0"),
                        GitHubRateLimitException.class),
                Arguments.of(404, Map.of(), GitHubPullRequestNotFoundException.class),
                Arguments.of(422, Map.of(), InvalidGitHubCommentException.class),
                Arguments.of(500, Map.of(), ExternalProviderUnavailableException.class));
    }

    @Test
    void connectionFailureIsTranslated() {
        int port = server.getAddress().getPort();
        server.stop(0);
        server = null;
        var client = new GitHubPullRequestClient(new GitHubApiClient(
                WebClient.builder(),
                new GitHubProperties("test-token", "http://127.0.0.1:" + port)));

        assertThatThrownBy(() -> client.createComment("owner", "repo", 42, "body"))
                .isInstanceOf(ExternalProviderUnavailableException.class);
    }

    private GitHubPullRequestClient client() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GitHubPullRequestClient(new GitHubApiClient(
                WebClient.builder(), new GitHubProperties("test-token", baseUrl)));
    }

    private void handle(HttpExchange exchange) throws IOException {
        method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().toString());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        accept.set(exchange.getRequestHeaders().getFirst("Accept"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Response configured = response.get();
        configured.headers().forEach((key, value) -> exchange.getResponseHeaders().add(key, value));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = configured.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(configured.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record Response(int status, String body, Map<String, String> headers) {}
}
