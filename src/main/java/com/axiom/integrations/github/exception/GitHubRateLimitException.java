package com.axiom.integrations.github.exception;
public class GitHubRateLimitException extends GitHubIntegrationException { public GitHubRateLimitException() { super("GitHub API rate limit has been exhausted."); } }
