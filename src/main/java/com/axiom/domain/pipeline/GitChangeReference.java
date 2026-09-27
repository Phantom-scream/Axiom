package com.axiom.domain.pipeline;

import java.util.UUID;

public record GitChangeReference(
        UUID pipelineRunId,
        UUID repositoryId,
        CiProviderType provider,
        String repositoryOwner,
        String repositoryName,
        String baseSha,
        String headSha,
        Long pullRequestNumber,
        String eventName) {}
