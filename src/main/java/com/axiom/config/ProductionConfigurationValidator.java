package com.axiom.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ProductionConfigurationValidator {
    private final GitHubProperties github;
    private final WebhookProperties webhook;

    public ProductionConfigurationValidator(GitHubProperties github, WebhookProperties webhook) {
        this.github = github;
        this.webhook = webhook;
    }

    @PostConstruct
    void validate() {
        validateBaseUrl();
        positive(github.effectiveConnectTimeout(), "GitHub connect timeout");
        positive(github.effectiveReadTimeout(), "GitHub read timeout");
        positive(github.effectiveRequestTimeout(), "GitHub request timeout");
        positive(github.effectiveRetryInitialBackoff(), "GitHub retry initial backoff");
        positive(github.effectiveRetryMaxBackoff(), "GitHub retry maximum backoff");
        if (webhook.enabledValue()
                && (github.webhookSecret() == null || github.webhookSecret().isBlank())) {
            throw new IllegalStateException(
                    "GitHub webhook processing is enabled but no webhook secret is configured.");
        }
        if ((github.autoPublishCheckEnabled() || github.autoPublishPrCommentEnabled())
                && (github.token() == null || github.token().isBlank())) {
            throw new IllegalStateException(
                    "Automatic GitHub publishing is enabled but no GitHub token is configured.");
        }
        if (webhook.effectiveCoreThreads() > webhook.effectiveMaxThreads()) {
            throw new IllegalStateException(
                    "Webhook core thread count must not exceed the maximum thread count.");
        }
        if (github.effectiveRetryInitialBackoff().compareTo(github.effectiveRetryMaxBackoff()) > 0) {
            throw new IllegalStateException(
                    "GitHub retry initial backoff must not exceed the maximum backoff.");
        }
    }

    private void positive(java.time.Duration value, String label) {
        if (value.toMillis() < 1 || value.compareTo(java.time.Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException(label + " must be positive and at most two minutes.");
        }
    }

    private void validateBaseUrl() {
        try {
            var uri = java.net.URI.create(github.resolvedBaseUrl());
            boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("https".equals(uri.getScheme()) || (loopback && "http".equals(uri.getScheme())))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("GitHub base URL must be HTTPS without credentials, query, or fragment (HTTP is allowed for loopback tests).");
        }
    }
}
