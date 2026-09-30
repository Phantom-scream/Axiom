package com.axiom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.limits")
public record OperationalLimitsProperties(
        @Min(65_536) @Max(104_857_600) Integer githubResponseBytes,
        @Min(65_536) @Max(104_857_600) Integer storedLogBytes,
        @Min(1_000) @Max(65_000) Integer publishedMarkdownCharacters) {
    public int effectiveGitHubResponseBytes() {
        return githubResponseBytes == null ? 25_165_824 : githubResponseBytes;
    }

    public int effectiveStoredLogBytes() {
        return storedLogBytes == null ? 25_165_824 : storedLogBytes;
    }

    public int effectivePublishedMarkdownCharacters() {
        return publishedMarkdownCharacters == null ? 60_000 : publishedMarkdownCharacters;
    }
}
