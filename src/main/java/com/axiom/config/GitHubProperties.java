package com.axiom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("axiom.github")
public record GitHubProperties(String token, String baseUrl) {
    public String resolvedBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.github.com" : baseUrl.replaceAll("/$", "");
    }
}
