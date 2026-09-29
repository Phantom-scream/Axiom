package com.axiom.domain.health;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.triage.RerunRecommendation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepositoryHealth(
        UUID repositoryId,
        Window window,
        Runs runs,
        Map<FailureClassification, Long> failureClassifications,
        Map<RerunRecommendation, Long> rerunRecommendations,
        Map<ChangeRelevance, Long> changeRelevance,
        TestStability testStability,
        List<TopFailureFingerprint> topFailureFingerprints) {
    public RepositoryHealth {
        failureClassifications = Map.copyOf(failureClassifications);
        rerunRecommendations = Map.copyOf(rerunRecommendations);
        changeRelevance = Map.copyOf(changeRelevance);
        topFailureFingerprints = List.copyOf(topFailureFingerprints);
    }

    public record Window(int days, int maxRuns, int analyzedRuns) {}

    public record Runs(
            int total, int successful, int failed, int cancelled, double successRate) {}

    public record TestStability(
            long suspectedFlaky, long flaky, long consistentlyFailing) {}

    public record TopFailureFingerprint(
            String fingerprint,
            long occurrenceCount,
            FailureClassification classification,
            Instant firstSeen,
            Instant lastSeen) {}
}
