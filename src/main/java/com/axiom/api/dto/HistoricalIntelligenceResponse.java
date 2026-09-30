package com.axiom.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Descriptive, bounded observations. No forecasts or causal ownership fields. */
public record HistoricalIntelligenceResponse<T>(UUID repositoryId, Window window, List<T> entries) {
    public record Window(int days, int maxRuns, int selectedRuns) {}
    public enum LifecycleStatus { ACTIVE, INTERMITTENT, RESOLVED, UNKNOWN }
    public enum Granularity { DAY, WEEK }
    public record FailureLifecycle(String fingerprint, Instant firstSeenAt, Instant lastSeenAt,
            long occurrenceCount, long affectedRunCount, String mostRecentClassification,
            List<String> affectedBranches, LifecycleStatus currentStatus) {}
    public record Incident(String fingerprint, String classification, long occurrenceCount, long affectedRunCount,
            Instant firstSeenAt, Instant lastSeenAt, LifecycleStatus lifecycleStatus,
            String commonTestStableId, String commonFileCategory, String moduleHint,
            long rerunRecoveryCount, long changeRelatedCount, long changeUnrelatedCount) {}
    public record Trend(LocalDate bucketStart, Granularity granularity, long totalRuns, long successfulRuns,
            long failedRuns, long cancelledRuns, double successRate, long newFailureFingerprints,
            long recurringFailureFingerprints, long rerunRecommendedCount, long changeRelatedFailureCount,
            long infrastructureFailureCount, long testFailureCount, long dependencyFailureCount) {}
    public record TestReliability(String stableTestId, String stabilityClass, long executionCount,
            long failureCount, long passCount, long recentFailToPassRerunCount,
            Instant firstFailureSeen, Instant lastFailureSeen) {}
    public record Hotspot(String moduleHint, long changedRunCount, long failureAssociatedRunCount,
            long relatedFailureCount, Map<String,Long> topFailureClassifications, List<String> topFingerprints) {}
}
