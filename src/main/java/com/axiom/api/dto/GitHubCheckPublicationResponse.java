package com.axiom.api.dto;

import com.axiom.application.publication.GitHubTriageCheckPublisher;
import java.util.UUID;

public record GitHubCheckPublicationResponse(
        UUID pipelineRunId,
        String externalCheckRunId,
        String externalUrl,
        String operation,
        String triageVersion) {
    public static GitHubCheckPublicationResponse from(
            GitHubTriageCheckPublisher.PublishResult result) {
        return new GitHubCheckPublicationResponse(
                result.pipelineRunId(),
                result.externalCheckRunId(),
                result.externalUrl(),
                result.operation(),
                result.triageVersion());
    }
}
