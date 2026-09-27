package com.axiom.integrations.github.exception;

public class InvalidGitHubComparisonException extends GitHubIntegrationException {
    public InvalidGitHubComparisonException() {
        super("GitHub rejected the base/head comparison as invalid.");
    }
}
