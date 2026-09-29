package com.axiom.api.dto;

import com.axiom.application.publication.GitHubPrTriagePublisher;
import java.util.UUID;

public record GitHubPrCommentPublicationResponse(
        UUID pipelineRunId,
        long pullRequestNumber,
        String externalCommentId,
        String externalUrl,
        String operation,
        String triageVersion) {
    public static GitHubPrCommentPublicationResponse from(
            GitHubPrTriagePublisher.PublishResult result) {
        return new GitHubPrCommentPublicationResponse(
                result.pipelineRunId(),
                result.pullRequestNumber(),
                result.externalCommentId(),
                result.externalUrl(),
                result.operation(),
                result.triageVersion());
    }
}
