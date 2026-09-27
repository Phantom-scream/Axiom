package com.axiom.domain.test;

import java.util.UUID;

public record RerunTransition(
        String stableTestId,
        long externalRunId,
        UUID fromPipelineRunId,
        UUID toPipelineRunId,
        int fromAttempt,
        int toAttempt,
        TestStatus fromStatus,
        TestStatus toStatus,
        String fromFingerprint,
        String toFingerprint,
        boolean sameCommitSha,
        RerunTransitionType transitionType) {}
