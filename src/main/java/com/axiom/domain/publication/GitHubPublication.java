package com.axiom.domain.publication;

import java.time.Instant;
import java.util.UUID;

public record GitHubPublication(
        UUID id,
        UUID pipelineRunId,
        String publicationType,
        String externalId,
        String externalUrl,
        String triageVersion,
        Instant publishedAt,
        Instant updatedAt) {}
