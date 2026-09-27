package com.axiom.api.dto;

import com.axiom.domain.test.RerunTransition;
import com.axiom.domain.test.RerunTransitionType;
import com.axiom.domain.test.TestStatus;
import java.util.UUID;

public record RerunTransitionResponse(
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
        RerunTransitionType transitionType) {
    public static RerunTransitionResponse from(RerunTransition transition) {
        return new RerunTransitionResponse(
                transition.stableTestId(),
                transition.externalRunId(),
                transition.fromPipelineRunId(),
                transition.toPipelineRunId(),
                transition.fromAttempt(),
                transition.toAttempt(),
                transition.fromStatus(),
                transition.toStatus(),
                transition.fromFingerprint(),
                transition.toFingerprint(),
                transition.sameCommitSha(),
                transition.transitionType());
    }
}
