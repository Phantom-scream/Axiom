package com.axiom.api.dto;

import java.util.UUID;

public record TestCorrelationResponse(
        UUID pipelineRunId,
        int failedExecutions,
        int exactMatches,
        int strongMatches,
        int uncorrelated) {}
