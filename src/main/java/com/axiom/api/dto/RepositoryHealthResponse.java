package com.axiom.api.dto;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.health.RepositoryHealth;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.triage.RerunRecommendation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryHealthResponse(
        UUID repositoryId,
        WindowResponse window,
        RunsResponse runs,
        Map<FailureClassification, Long> failureClassifications,
        Map<RerunRecommendation, Long> rerunRecommendations,
        Map<ChangeRelevance, Long> changeRelevance,
        TestStabilityResponse testStability,
        List<TopFailureFingerprintResponse> topFailureFingerprints) {
    public static RepositoryHealthResponse from(RepositoryHealth health) {
        return new RepositoryHealthResponse(
                health.repositoryId(),
                new WindowResponse(
                        health.window().days(),
                        health.window().maxRuns(),
                        health.window().analyzedRuns()),
                new RunsResponse(
                        health.runs().total(),
                        health.runs().successful(),
                        health.runs().failed(),
                        health.runs().cancelled(),
                        health.runs().successRate()),
                health.failureClassifications(),
                health.rerunRecommendations(),
                health.changeRelevance(),
                new TestStabilityResponse(
                        health.testStability().suspectedFlaky(),
                        health.testStability().flaky(),
                        health.testStability().consistentlyFailing()),
                health.topFailureFingerprints().stream()
                        .map(TopFailureFingerprintResponse::from)
                        .toList());
    }

    public record WindowResponse(int days, int maxRuns, int analyzedRuns) {}

    public record RunsResponse(
            int total, int successful, int failed, int cancelled, double successRate) {}

    public record TestStabilityResponse(
            long suspectedFlaky, long flaky, long consistentlyFailing) {}

    public record TopFailureFingerprintResponse(
            String fingerprint,
            long occurrenceCount,
            FailureClassification classification,
            Instant firstSeen,
            Instant lastSeen) {
        static TopFailureFingerprintResponse from(
                RepositoryHealth.TopFailureFingerprint value) {
            return new TopFailureFingerprintResponse(
                    value.fingerprint(),
                    value.occurrenceCount(),
                    value.classification(),
                    value.firstSeen(),
                    value.lastSeen());
        }
    }
}
