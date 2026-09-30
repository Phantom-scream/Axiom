package com.axiom.integrations.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWorkflowRunWebhookDto(
        String action,
        @JsonProperty("workflow_run") GitHubWorkflowRunDto workflowRun,
        GitHubRepositoryDto repository) {}
