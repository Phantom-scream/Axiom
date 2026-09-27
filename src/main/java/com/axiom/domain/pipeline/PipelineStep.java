package com.axiom.domain.pipeline;

import java.time.Instant;

public record PipelineStep(int number, String name, PipelineStatus status, PipelineConclusion conclusion, Instant startedAt, Instant finishedAt) {}
