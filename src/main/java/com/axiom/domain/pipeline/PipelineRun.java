package com.axiom.domain.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PipelineRun(UUID id, CiProviderType provider, long externalRunId, String repositoryOwner, String repositoryName, String commitSha, String branch, Long pullRequestNumber, PipelineStatus status, PipelineConclusion conclusion, int attempt, Instant startedAt, Instant finishedAt, List<PipelineJob> jobs) {
    public PipelineRun { jobs = List.copyOf(jobs); }
}
