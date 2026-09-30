package com.axiom.integrations.github.exception;

import java.time.Duration;
import java.time.Instant;

public class GitHubRateLimitException extends GitHubIntegrationException {
    private final Duration retryAfter;
    private final Instant resetAt;

    public GitHubRateLimitException() {
        this(null, null);
    }

    public GitHubRateLimitException(Duration retryAfter, Instant resetAt) {
        super("GitHub API rate limit has been exhausted.");
        this.retryAfter = retryAfter;
        this.resetAt = resetAt;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public Instant resetAt() {
        return resetAt;
    }
}
