package com.axiom.integrations.github.exception;
public class GitHubAuthenticationException extends GitHubIntegrationException { public GitHubAuthenticationException() { super("GitHub authentication failed. Configure a valid AXIOM_GITHUB_TOKEN."); } }
