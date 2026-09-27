package com.axiom.domain.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PipelineJob(UUID id, long externalJobId, String name, PipelineStatus status, PipelineConclusion conclusion, String runnerName, Instant startedAt, Instant finishedAt, List<PipelineStep> steps) {
    public PipelineJob { steps = List.copyOf(steps); }
}
