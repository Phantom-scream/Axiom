package com.axiom.domain.triage;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.test.TestStabilityClass;
import java.util.UUID;

public record FailureTriageEntry(
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
        boolean repeatedSameFailure) {}
