package com.axiom.integrations.github.exception;

public class GitHubPullRequestNotFoundException extends GitHubIntegrationException {
    public GitHubPullRequestNotFoundException() {
        super("The GitHub repository, pull request, or issue comment was not found.");
    }
}
