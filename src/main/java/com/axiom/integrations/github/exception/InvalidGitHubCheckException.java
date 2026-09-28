package com.axiom.integrations.github.exception;

public class InvalidGitHubCheckException extends GitHubIntegrationException {
    public InvalidGitHubCheckException() {
        super("GitHub rejected the Axiom Check Run request as invalid.");
    }
}
