package com.axiom.integrations.github.exception;
public class ExternalProviderUnavailableException extends GitHubIntegrationException {
    private final boolean retryable;
    public ExternalProviderUnavailableException() { this(true); }
    public ExternalProviderUnavailableException(boolean retryable) {
        super("GitHub is temporarily unavailable.");
        this.retryable = retryable;
    }
    public boolean retryable() { return retryable; }
}
