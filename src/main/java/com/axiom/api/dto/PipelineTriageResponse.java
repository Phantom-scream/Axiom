package com.axiom.api.dto;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.triage.ActionType;
import com.axiom.domain.triage.FailureRole;
import com.axiom.domain.triage.PipelineTriageResult;
import com.axiom.domain.triage.RerunRecommendation;
import com.axiom.domain.triage.TriageEvidencePriority;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PipelineTriageResponse(
        UUID pipelineRunId,
        String status,
        String triageVersion,
        PrimaryFailureResponse primaryFailure,
        ChangeRelevance changeRelevance,
        RerunRecommendation rerunRecommendation,
        double rerunConfidence,
        String summary,
        List<ActionResponse> actions,
        List<FailureResponse> failures,
        List<EvidenceResponse> evidence,
        Instant createdAt) {
    public static PipelineTriageResponse from(PipelineTriageResult result) {
        PrimaryFailureResponse primary = result.primaryFailureEventId() == null
                ? null
                : result.failures().stream()
                        .filter(failure -> failure.failureEventId().equals(result.primaryFailureEventId()))
                        .findFirst()
                        .map(failure -> new PrimaryFailureResponse(
                                failure.failureEventId(),
                                failure.fingerprint(),
                                failure.classification(),
                                failure.role(),
                                failure.importanceScore()))
                        .orElse(null);
        return new PipelineTriageResponse(
                result.pipelineRunId(),
                result.status(),
                result.triageVersion(),
                primary,
                result.changeRelevance(),
                result.rerunRecommendation(),
                result.rerunConfidence(),
                result.summary(),
                result.actions().stream().map(ActionResponse::from).toList(),
                result.failures().stream().map(FailureResponse::from).toList(),
                result.evidence().stream().map(EvidenceResponse::from).toList(),
                result.createdAt());
    }

    public record PrimaryFailureResponse(
            UUID failureEventId,
            String fingerprint,
            FailureClassification classification,
            FailureRole role,
            double importanceScore) {}

    public record FailureResponse(
            UUID failureEventId,
            String fingerprint,
            FailureClassification classification,
            FailureRole role,
            double importanceScore,
            ChangeRelevance changeRelevance,
            int historicalOccurrenceCount,
            int correlatedTests,
            TestStabilityClass testStability,
            boolean unchangedRerunPassed,
            boolean repeatedSameFailure) {
        static FailureResponse from(com.axiom.domain.triage.FailureTriageEntry entry) {
            return new FailureResponse(
                    entry.failureEventId(),
                    entry.fingerprint(),
                    entry.classification(),
                    entry.role(),
                    entry.importanceScore(),
                    entry.changeRelevance(),
                    entry.historicalOccurrenceCount(),
                    entry.correlatedTests(),
                    entry.testStability(),
                    entry.unchangedRerunPassed(),
                    entry.repeatedSameFailure());
        }
    }

    public record ActionResponse(
            int priority, ActionType type, String description, String reason) {
        static ActionResponse from(com.axiom.domain.triage.RecommendedAction action) {
            return new ActionResponse(
                    action.priority(), action.type(), action.description(), action.reason());
        }
    }

    public record EvidenceResponse(
            UUID failureEventId,
            String code,
            TriageEvidencePriority priority,
            double weight,
            String description,
            Map<String, String> metadata) {
        static EvidenceResponse from(com.axiom.domain.triage.TriageEvidence evidence) {
            return new EvidenceResponse(
                    evidence.failureEventId(),
                    evidence.code(),
                    evidence.priority(),
                    evidence.weight(),
                    evidence.description(),
                    evidence.metadata());
        }
    }
}
