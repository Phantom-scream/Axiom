package com.axiom.domain.triage;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PipelineTriageResult(
        UUID id,
        UUID pipelineRunId,
        String status,
        UUID primaryFailureEventId,
        String primaryFingerprint,
        FailureRole primaryFailureRole,
        FailureClassification primaryClassification,
        ChangeRelevance changeRelevance,
        RerunRecommendation rerunRecommendation,
        double rerunConfidence,
        String summary,
        String triageVersion,
        Instant createdAt,
        List<RecommendedAction> actions,
        List<FailureTriageEntry> failures,
        List<TriageEvidence> evidence) {
    public PipelineTriageResult {
        actions = List.copyOf(actions);
        failures = List.copyOf(failures);
        evidence = List.copyOf(evidence);
    }
}
