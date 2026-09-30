package com.axiom.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WebClientConfigTest {
    @Test
    void downloadsRedirectedLogsWithoutForwardingCredentials() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/logs", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://localhost:" + server.getAddress().getPort() + "/signed");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/signed", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = "safe fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var builder = new WebClientConfig().webClientBuilder(new GitHubProperties("mock", null), new OperationalLimitsProperties(null, null, null));
            byte[] result = builder.build().get().uri("http://localhost:" + server.getAddress().getPort() + "/logs")
                    .header("Authorization", "Bearer mock-secret").retrieve().bodyToMono(byte[].class).block(java.time.Duration.ofSeconds(5));
            assertThat(result).isEqualTo("safe fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(authorization.get()).isNull();
        } finally { server.stop(0); }
    }
}
