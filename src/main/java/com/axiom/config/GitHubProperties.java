package com.axiom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.github")
public record GitHubProperties(
        String token,
        String baseUrl,
        String webhookSecret,
        Boolean autoPublishCheck,
        Boolean autoPublishPrComment,
        Duration connectTimeout,
        Duration readTimeout,
        Duration requestTimeout,
        @Min(1) @Max(5) Integer retryMaxAttempts,
        Duration retryInitialBackoff,
        Duration retryMaxBackoff) {
    @ConstructorBinding
    public GitHubProperties {}

    public GitHubProperties(String token, String baseUrl) {
        this(token, baseUrl, null, false, false, null, null, null, 1, null, null);
    }

    public String resolvedBaseUrl() {
        return baseUrl == null || baseUrl.isBlank() ? "https://api.github.com" : baseUrl.replaceAll("/$", "");
    }

    public boolean autoPublishCheckEnabled() {
        return Boolean.TRUE.equals(autoPublishCheck);
    }

    public boolean autoPublishPrCommentEnabled() {
        return Boolean.TRUE.equals(autoPublishPrComment);
    }

    public Duration effectiveConnectTimeout() {
        return connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
    }

    public Duration effectiveReadTimeout() {
        return readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    public Duration effectiveRequestTimeout() {
        return requestTimeout == null ? Duration.ofSeconds(35) : requestTimeout;
    }

    public int effectiveRetryMaxAttempts() {
        return retryMaxAttempts == null ? 3 : retryMaxAttempts;
    }

    public Duration effectiveRetryInitialBackoff() {
        return retryInitialBackoff == null ? Duration.ofMillis(200) : retryInitialBackoff;
    }

    public Duration effectiveRetryMaxBackoff() {
        return retryMaxBackoff == null ? Duration.ofSeconds(2) : retryMaxBackoff;
    }

    @Override
    public String toString() {
        return "GitHubProperties[token=<redacted>, webhookSecret=<redacted>]";
    }
}
