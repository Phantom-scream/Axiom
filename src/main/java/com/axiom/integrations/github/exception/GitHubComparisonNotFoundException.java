package com.axiom.integrations.github.exception;

public class GitHubComparisonNotFoundException extends GitHubIntegrationException {
    public GitHubComparisonNotFoundException() {
        super("GitHub could not find the requested repository or comparison.");
    }
}
