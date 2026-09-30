package com.axiom.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {
    @Bean
    WebClient.Builder webClientBuilder(
            GitHubProperties properties, OperationalLimitsProperties limits) {
        HttpClient client = HttpClient.create()
                .followRedirect((request, response) -> {
                    int status = response.status().code();
                    if (!request.method().name().equals("GET") || request.redirectedFrom().length >= 3
                            || !(status == 301 || status == 302 || status == 303 || status == 307 || status == 308)) return false;
                    String location = response.responseHeaders().get("Location");
                    if (location == null) return false;
                    try {
                        var target = java.net.URI.create(request.resourceUrl()).resolve(location);
                        boolean loopback = "localhost".equals(target.getHost()) || "127.0.0.1".equals(target.getHost());
                        return target.getUserInfo() == null && ("https".equals(target.getScheme())
                                || (loopback && "http".equals(target.getScheme())));
                    } catch (IllegalArgumentException invalid) { return false; }
                }, request -> {
                    // Actions logs use short-lived signed download URLs. Never forward API credentials.
                    request.requestHeaders().remove("Authorization");
                    request.requestHeaders().remove("Cookie");
                })
                .option(
                        ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(properties.effectiveConnectTimeout().toMillis()))
                .responseTimeout(properties.effectiveReadTimeout());
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client))
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(limits.effectiveGitHubResponseBytes()));
    }
}
