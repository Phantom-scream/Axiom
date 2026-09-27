package com.axiom.integrations.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.axiom.config.GitHubProperties;
import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.GitChangeReference;
import com.axiom.integrations.github.client.GitHubApiClient;
import com.axiom.integrations.github.exception.ExternalProviderUnavailableException;
import com.axiom.integrations.github.exception.GitHubAuthenticationException;
import com.axiom.integrations.github.exception.GitHubComparisonNotFoundException;
import com.axiom.integrations.github.exception.GitHubPermissionException;
import com.axiom.integrations.github.exception.GitHubRateLimitException;
import com.axiom.integrations.github.exception.InvalidGitHubComparisonException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.reactive.function.client.WebClient;

class GitHubChangeProviderHttpTest {
    private HttpServer server;
    private final AtomicReference<Response> response = new AtomicReference<>();
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
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
    void mapsCompareResponseThroughRealHttpClient() {
        response.set(new Response(200, """
                {
                  "status":"ahead",
                  "ahead_by":2,
                  "behind_by":0,
                  "base_commit":{"sha":"base"},
                  "files":[
                    {"filename":"src/main/App.java","status":"modified","additions":3,"deletions":1,"changes":4,"patch":"not retained"},
                    {"filename":"src/main/New.java","status":"added","additions":5,"deletions":0,"changes":5},
                    {"filename":"src/Old.java","status":"removed","additions":0,"deletions":2,"changes":2},
                    {"filename":"src/NewName.java","previous_filename":"src/OldName.java","status":"renamed","additions":1,"deletions":1,"changes":2},
                    {"filename":"src/Copy.java","status":"copied","additions":2,"deletions":0,"changes":2},
                    {"filename":"assets/value.bin","status":"mystery","additions":0,"deletions":0,"changes":0}
                  ]
                }
                """, Map.of()));

        var changeSet = provider().fetchChanges(reference());

        assertThat(requestedPath.get()).isEqualTo("/repos/owner/repo/compare/base...head");
        assertThat(authorization.get()).isEqualTo("Bearer test-token");
        assertThat(accept.get()).contains("application/vnd.github+json");
        assertThat(changeSet.baseSha()).isEqualTo("base");
        assertThat(changeSet.headSha()).isEqualTo("head");
        assertThat(changeSet.totalChangedFiles()).isEqualTo(6);
        assertThat(changeSet.totalAdditions()).isEqualTo(11);
        assertThat(changeSet.totalDeletions()).isEqualTo(4);
        assertThat(changeSet.files()).extracting(file -> file.changeType())
                .containsExactly(
                        ChangeType.MODIFIED,
                        ChangeType.ADDED,
                        ChangeType.DELETED,
                        ChangeType.RENAMED,
                        ChangeType.COPIED,
                        ChangeType.UNKNOWN);
        assertThat(changeSet.files().get(3).previousPath()).isEqualTo("src/OldName.java");
    }

    @ParameterizedTest
    @MethodSource("errors")
    void translatesGitHubErrors(
            int status,
            Map<String, String> headers,
            Class<? extends RuntimeException> expected) {
        response.set(new Response(status, "{\"message\":\"failure\"}", headers));

        assertThatThrownBy(() -> provider().fetchChanges(reference())).isInstanceOf(expected);
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(401, Map.of(), GitHubAuthenticationException.class),
                Arguments.of(403, Map.of(), GitHubPermissionException.class),
                Arguments.of(
                        403,
                        Map.of("X-RateLimit-Remaining", "0"),
                        GitHubRateLimitException.class),
                Arguments.of(404, Map.of(), GitHubComparisonNotFoundException.class),
                Arguments.of(422, Map.of(), InvalidGitHubComparisonException.class),
                Arguments.of(500, Map.of(), ExternalProviderUnavailableException.class));
    }

    @Test
    void connectionFailureIsTranslatedAsProviderUnavailable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        server = null;
        var client = new GitHubApiClient(
                WebClient.builder(),
                new GitHubProperties("test-token", "http://127.0.0.1:" + port));
        var unavailableProvider = new GitHubChangeProvider(client);

        assertThatThrownBy(() -> unavailableProvider.fetchChanges(reference()))
                .isInstanceOf(ExternalProviderUnavailableException.class);
    }

    private GitHubChangeProvider provider() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        var client = new GitHubApiClient(
                WebClient.builder(), new GitHubProperties("test-token", baseUrl));
        return new GitHubChangeProvider(client);
    }

    private GitChangeReference reference() {
        return new GitChangeReference(
                UUID.randomUUID(),
                UUID.randomUUID(),
                CiProviderType.GITHUB_ACTIONS,
                "owner",
                "repo",
                "base",
                "head",
                7L,
                "pull_request");
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestedPath.set(exchange.getRequestURI().toString());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        accept.set(exchange.getRequestHeaders().getFirst("Accept"));
        Response configured = response.get();
        configured.headers().forEach((key, value) -> exchange.getResponseHeaders().add(key, value));
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] body = configured.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(configured.status(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private record Response(int status, String body, Map<String, String> headers) {}
}
