package com.axiom.integrations.github.exception;

public class InvalidGitHubCommentException extends GitHubIntegrationException {
    public InvalidGitHubCommentException() {
        super("GitHub rejected the Axiom pull-request comment as invalid.");
    }
}
