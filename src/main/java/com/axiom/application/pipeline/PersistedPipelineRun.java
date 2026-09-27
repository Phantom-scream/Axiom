package com.axiom.application.pipeline;
import java.time.Instant;
import java.util.UUID;
public record PersistedPipelineRun(UUID id, int attempt, int jobCount, int stepCount, Instant ingestedAt) {}
