package com.axiom.integrations.github.exception;

public class GitHubCheckTargetNotFoundException extends GitHubIntegrationException {
    public GitHubCheckTargetNotFoundException() {
        super("The GitHub repository, commit, or Check Run was not found.");
    }
}
