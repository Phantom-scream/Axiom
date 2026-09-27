package com.axiom.integrations.github.exception;
public class PipelineRunNotFoundException extends GitHubIntegrationException { public PipelineRunNotFoundException(long id) { super("GitHub Actions run " + id + " was not found."); } }
