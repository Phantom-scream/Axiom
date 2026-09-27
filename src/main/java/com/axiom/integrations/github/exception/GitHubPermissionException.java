package com.axiom.integrations.github.exception;
public class GitHubPermissionException extends GitHubIntegrationException { public GitHubPermissionException() { super("GitHub denied access to this resource."); } }
