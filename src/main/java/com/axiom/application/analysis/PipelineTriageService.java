package com.axiom.application.analysis;

import com.axiom.config.TriageProperties;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.triage.FailureRole;
import com.axiom.domain.triage.FailureTriageEntry;
import com.axiom.domain.triage.RerunRecommendation;
import com.axiom.domain.triage.TriageEvidence;
import com.axiom.domain.triage.TriageEvidencePriority;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PipelineTriageService {
    private final TriageProperties properties;

    public PipelineTriageService(TriageProperties properties) {
        this.properties = properties;
    }

    public PipelineTriageService() {
        this(new TriageProperties("triage-v1", 3, .6, .1));
    }

    public Result triage(
            List<Failure> failures, boolean unchangedRerunPass, boolean relatedChange) {
        return triage(failures.stream()
                .map(failure -> failure.withLegacyEvidence(unchangedRerunPass, relatedChange))
                .toList());
    }

    public Result triage(List<Failure> failures) {
        if (failures.isEmpty()) {
            return new Result(
                    null,
                    null,
                    FailureRole.UNKNOWN.name(),
                    null,
                    RerunRecommendation.INSUFFICIENT_EVIDENCE.name(),
                    0,
                    "No extracted failure events are available; triage has insufficient evidence.",
                    List.of(),
                    List.of(evidence(
                            null,
                            "NO_FAILURE_EVENTS",
                            TriageEvidencePriority.WEAK,
                            0,
                            "No extracted failure events are available.")));
        }

        List<ScoredFailure> ranked = failures.stream()
                .map(failure -> new ScoredFailure(failure, importanceScore(failure)))
                .sorted(Comparator.comparingDouble(ScoredFailure::score)
                        .reversed()
                        .thenComparingInt(scored -> sortableLine(scored.failure().firstLine()))
                        .thenComparing(scored -> scored.failure().fingerprint()))
                .toList();
        ScoredFailure top = ranked.getFirst();
        boolean dominant = top.score() >= properties.effectivePrimaryMinimumScore()
                && (ranked.size() == 1
                        || top.score() - ranked.get(1).score()
                                >= properties.effectivePrimaryDominanceMargin());
        Failure primary = dominant ? top.failure() : null;

        List<FailureTriageEntry> entries = new ArrayList<>();
        List<TriageEvidence> evidence = new ArrayList<>();
        for (ScoredFailure scored : ranked) {
            FailureRole role = role(scored, primary);
            entries.add(new FailureTriageEntry(
                    scored.failure().failureEventId(),
                    scored.failure().fingerprint(),
                    scored.failure().classification(),
                    role,
                    scored.score(),
                    scored.failure().changeRelevance(),
                    scored.failure().historicalOccurrenceCount(),
                    scored.failure().correlatedTests(),
                    scored.failure().testStability(),
                    scored.failure().unchangedRerunPassed(),
                    scored.failure().repeatedSameFailure()));
            evidence.add(rankingEvidence(scored.failure(), role, scored.score()));
            if (scored.failure().generic()) {
                evidence.add(evidence(
                        scored.failure().failureEventId(),
                        "GENERIC_SYMPTOM_PENALTY",
                        TriageEvidencePriority.COUNTER,
                        -.5,
                        "A generic command/build marker is weaker than a specific failure signal."));
            }
        }
        if (!dominant) {
            evidence.add(evidence(
                    null,
                    "NO_DOMINANT_PRIMARY_FAILURE",
                    TriageEvidencePriority.WEAK,
                    0,
                    "No failure signal exceeded the configured dominance margin."));
        }

        RerunDecision rerun = rerunDecision(primary, failures);
        evidence.addAll(rerun.evidence());
        return new Result(
                primary == null ? null : primary.failureEventId(),
                primary == null ? null : primary.fingerprint(),
                primary == null ? FailureRole.UNKNOWN.name() : FailureRole.PRIMARY.name(),
                primary == null ? null : primary.classification(),
                rerun.recommendation().name(),
                rerun.confidence(),
                summary(primary, rerun.recommendation()),
                List.copyOf(entries),
                List.copyOf(evidence));
    }

    private double importanceScore(Failure failure) {
        double score = switch (failure.classification()) {
            case RESOURCE_EXHAUSTION -> .95;
            case INFRASTRUCTURE_FAILURE -> .92;
            case DEPENDENCY_FAILURE -> .9;
            case EXTERNAL_SERVICE_FAILURE -> .87;
            case CONFIGURATION_FAILURE -> .84;
            case ENVIRONMENT_FAILURE -> .82;
            case BUILD_FAILURE, PRODUCT_REGRESSION -> .78;
            case TIMEOUT -> .62;
            case TEST_FAILURE, FLAKY_TEST -> .56;
            case UNKNOWN -> .3;
        };
        if (failure.generic()) score -= .5;
        if (failure.correlatedTests() > 0) score += .05;
        if (failure.changeRelevance() == ChangeRelevance.RELATED) score += .03;
        return Math.max(0, Math.min(1, score));
    }

    private FailureRole role(ScoredFailure failure, Failure primary) {
        if (primary != null && failure.failure().failureEventId().equals(primary.failureEventId())) {
            return FailureRole.PRIMARY;
        }
        if (failure.failure().generic()) return FailureRole.DOWNSTREAM;
        if (primary != null
                && isCascadeSource(primary.classification())
                && (failure.failure().classification() == FailureClassification.TEST_FAILURE
                        || failure.failure().classification() == FailureClassification.BUILD_FAILURE)) {
            return FailureRole.DOWNSTREAM;
        }
        if (failure.score() >= properties.effectivePrimaryMinimumScore()) {
            return FailureRole.CONTRIBUTING;
        }
        return primary == null ? FailureRole.UNKNOWN : FailureRole.SECONDARY;
    }

    private boolean isCascadeSource(FailureClassification classification) {
        return classification == FailureClassification.INFRASTRUCTURE_FAILURE
                || classification == FailureClassification.RESOURCE_EXHAUSTION
                || classification == FailureClassification.DEPENDENCY_FAILURE
                || classification == FailureClassification.EXTERNAL_SERVICE_FAILURE;
    }

    private RerunDecision rerunDecision(Failure primary, List<Failure> failures) {
        if (primary == null) {
            return new RerunDecision(
                    RerunRecommendation.INSUFFICIENT_EVIDENCE, 0, List.of());
        }
        List<TriageEvidence> evidence = new ArrayList<>();
        double score = 0;
        if (failures.stream().anyMatch(Failure::unchangedRerunPassed)) {
            score += .8;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "UNCHANGED_RERUN_PASSED",
                    TriageEvidencePriority.STRONG,
                    .8,
                    "An unchanged workflow rerun passed for correlated test history."));
        }
        if (primary.classification() == FailureClassification.INFRASTRUCTURE_FAILURE
                || primary.classification() == FailureClassification.ENVIRONMENT_FAILURE
                || primary.classification() == FailureClassification.EXTERNAL_SERVICE_FAILURE) {
            score += .35;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "TRANSIENT_FAILURE_CLASSIFICATION",
                    TriageEvidencePriority.MEDIUM,
                    .35,
                    "The primary classification can represent a transient operational condition."));
        }
        if (primary.changeRelevance() == ChangeRelevance.UNRELATED
                || primary.changeRelevance() == ChangeRelevance.UNLIKELY_RELATED) {
            score += .35;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "CHANGE_UNLIKELY_RELATED",
                    TriageEvidencePriority.MEDIUM,
                    .35,
                    "Persisted change relevance is unrelated or unlikely related."));
        }
        if (primary.historicalOccurrenceCount() > 0) {
            score += .2;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "FAILURE_PREDATES_CURRENT_RUN",
                    TriageEvidencePriority.WEAK,
                    .2,
                    "The same failure fingerprint occurred before this pipeline run."));
        }
        if ((primary.testStability() == TestStabilityClass.FLAKY
                        || primary.testStability() == TestStabilityClass.SUSPECTED_FLAKY)
                && primary.unchangedRerunPassed()) {
            score += .35;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "UNSTABLE_TEST_WITH_RERUN_EVIDENCE",
                    TriageEvidencePriority.STRONG,
                    .35,
                    "The correlated test has instability classification and unchanged-rerun evidence."));
        }
        if (primary.classification() == FailureClassification.DEPENDENCY_FAILURE
                || primary.classification() == FailureClassification.BUILD_FAILURE) {
            score -= .65;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "DETERMINISTIC_FAILURE_DISCOURAGES_RERUN",
                    TriageEvidencePriority.COUNTER,
                    -.65,
                    "Build and dependency failures are unlikely to change without an input change."));
        }
        if (primary.changeRelevance() == ChangeRelevance.RELATED
                && (primary.classification() == FailureClassification.DEPENDENCY_FAILURE
                        || primary.classification() == FailureClassification.BUILD_FAILURE
                        || primary.classification() == FailureClassification.CONFIGURATION_FAILURE
                        || primary.classification() == FailureClassification.TEST_FAILURE)) {
            score -= .6;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "RELATED_CHANGE_DISCOURAGES_RERUN",
                    TriageEvidencePriority.COUNTER,
                    -.6,
                    "Strong persisted change relevance indicates investigation should precede a rerun."));
        }
        if (primary.testStability() == TestStabilityClass.CONSISTENTLY_FAILING) {
            score -= .8;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "CONSISTENT_TEST_FAILURE",
                    TriageEvidencePriority.COUNTER,
                    -.8,
                    "The correlated test is consistently failing."));
        }
        if (primary.repeatedSameFailure()) {
            score -= .65;
            evidence.add(evidence(
                    primary.failureEventId(),
                    "REPEATED_SAME_FAILURE_ON_RERUN",
                    TriageEvidencePriority.COUNTER,
                    -.65,
                    "The same failure persisted across an unchanged rerun."));
        }
        if (score == 0 && primary.classification() == FailureClassification.RESOURCE_EXHAUSTION) {
            score = .25;
        }
        RerunRecommendation recommendation;
        if (score >= .7) recommendation = RerunRecommendation.RECOMMENDED;
        else if (score <= -.6) recommendation = RerunRecommendation.NOT_RECOMMENDED;
        else if (Math.abs(score) >= .2) recommendation = RerunRecommendation.CONSIDER;
        else recommendation = RerunRecommendation.INSUFFICIENT_EVIDENCE;
        return new RerunDecision(
                recommendation, Math.min(1, Math.abs(score)), List.copyOf(evidence));
    }

    private TriageEvidence rankingEvidence(
            Failure failure, FailureRole role, double score) {
        return new TriageEvidence(
                failure.failureEventId(),
                role == FailureRole.PRIMARY ? "PRIMARY_SIGNAL_SELECTED" : "FAILURE_SIGNAL_RANKED",
                role == FailureRole.PRIMARY
                        ? TriageEvidencePriority.ROOT
                        : TriageEvidencePriority.MEDIUM,
                score,
                role == FailureRole.PRIMARY
                        ? "This specific signal has the highest supported diagnostic importance."
                        : "This failure was ranked using its classification specificity and context.",
                Map.of("classification", failure.classification().name()));
    }

    private TriageEvidence evidence(
            UUID failureEventId,
            String code,
            TriageEvidencePriority priority,
            double weight,
            String description) {
        return new TriageEvidence(
                failureEventId, code, priority, weight, description, Map.of());
    }

    private String summary(Failure primary, RerunRecommendation rerun) {
        if (primary == null) {
            return "No failure clearly dominates; investigate the ranked signals before acting.";
        }
        String classification = primary.classification().name().toLowerCase().replace('_', ' ');
        String relevance = primary.changeRelevance() == null
                ? "Change relevance is unavailable."
                : "Its persisted change relevance is "
                        + primary.changeRelevance().name().toLowerCase().replace('_', ' ')
                        + ".";
        String rerunText = switch (rerun) {
            case RECOMMENDED -> "A rerun is recommended by the available evidence.";
            case CONSIDER -> "A rerun may be considered, but the evidence is mixed.";
            case NOT_RECOMMENDED -> "Investigation is recommended before rerunning.";
            case INSUFFICIENT_EVIDENCE -> "There is insufficient evidence for rerun guidance.";
        };
        return "A " + classification + " appears to be the highest-priority failure signal. "
                + relevance
                + " "
                + rerunText;
    }

    private int sortableLine(Integer line) {
        return line == null ? Integer.MAX_VALUE : line;
    }

    public record Failure(
            UUID failureEventId,
            String fingerprint,
            FailureClassification classification,
            boolean generic,
            Integer firstLine,
            int historicalOccurrenceCount,
            ChangeRelevance changeRelevance,
            int correlatedTests,
            TestStabilityClass testStability,
            boolean unchangedRerunPassed,
            boolean repeatedSameFailure) {
        public Failure(
                String fingerprint, FailureClassification classification, boolean generic) {
            this(
                    UUID.nameUUIDFromBytes(fingerprint.getBytes(StandardCharsets.UTF_8)),
                    fingerprint,
                    classification,
                    generic,
                    null,
                    0,
                    null,
                    0,
                    null,
                    false,
                    false);
        }

        Failure withLegacyEvidence(boolean unchangedRerunPass, boolean relatedChange) {
            return new Failure(
                    failureEventId,
                    fingerprint,
                    classification,
                    generic,
                    firstLine,
                    historicalOccurrenceCount,
                    relatedChange ? ChangeRelevance.RELATED : changeRelevance,
                    correlatedTests,
                    testStability,
                    unchangedRerunPass || unchangedRerunPassed,
                    repeatedSameFailure);
        }
    }

    public record Result(
            UUID primaryFailureEventId,
            String primaryFingerprint,
            String primaryRole,
            FailureClassification primaryClassification,
            String rerunRecommendation,
            double rerunConfidence,
            String summary,
            List<FailureTriageEntry> failureRankings,
            List<TriageEvidence> evidence) {}

    private record ScoredFailure(Failure failure, double score) {}

    private record RerunDecision(
            RerunRecommendation recommendation,
            double confidence,
            List<TriageEvidence> evidence) {}
}
