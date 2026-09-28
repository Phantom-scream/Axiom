package com.axiom.analysis.correlation;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.relevance.ChangeRelevanceEvidence;
import com.axiom.domain.relevance.FailureChangeContext;
import com.axiom.domain.relevance.RelatedChangedFile;
import com.axiom.domain.relevance.RelevanceEvidencePriority;
import com.axiom.domain.test.CorrelationStrength;
import com.axiom.domain.test.RerunTransitionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ChangeRelevanceService {
    public static final String VERSION = "change-relevance-v1";

    public Result analyze(
            String failureCategory,
            Collection<FileCategory> files,
            boolean predates,
            boolean rerunPassed) {
        double score = baseScore(failureCategory, files, predates, rerunPassed);
        return new Result(classify(score).name(), confidence(score), VERSION);
    }

    public DetailedResult analyze(FailureChangeContext context) {
        List<FileCategory> categories = context.changedFiles().stream()
                .map(file -> file.file().fileCategory())
                .toList();
        boolean predates = context.fingerprintHistory().predatesCurrentRun();
        boolean unchangedRerunPassed = context.rerunTransitions().stream()
                .anyMatch(transition -> transition.sameCommitSha()
                        && (transition.transitionType() == RerunTransitionType.FAIL_TO_PASS
                                || transition.transitionType()
                                        == RerunTransitionType.ERROR_TO_PASS));

        List<ChangeRelevanceEvidence> evidence = new ArrayList<>();
        List<RelatedChangedFile> relatedFiles = new ArrayList<>();
        double score = baseScore(context.classification().name(), categories, predates, unchangedRerunPassed);

        if (predates) {
            evidence.add(evidence(
                    "SAME_FINGERPRINT_PREDATES_CHANGE",
                    RelevanceEvidencePriority.COUNTER,
                    -.7,
                    "The same failure fingerprint occurred in earlier pipeline runs.",
                    "failure-history",
                    Map.of("priorOccurrences", Integer.toString(context.fingerprintHistory().priorOccurrences()))));
        } else {
            evidence.add(evidence(
                    "NEW_FAILURE_FINGERPRINT",
                    RelevanceEvidencePriority.WEAK,
                    .15,
                    "This failure fingerprint was not found in the bounded prior-run history.",
                    "failure-history",
                    Map.of()));
            score += .15;
        }
        if (unchangedRerunPassed) {
            evidence.add(evidence(
                    "UNCHANGED_RERUN_FAIL_TO_PASS",
                    RelevanceEvidencePriority.COUNTER,
                    -.6,
                    "The correlated test previously failed and then passed on an unchanged workflow rerun.",
                    "test-rerun-history",
                    Map.of()));
        }
        if (!categories.isEmpty()
                && categories.stream().allMatch(category -> category == FileCategory.DOCUMENTATION)) {
            evidence.add(evidence(
                    "ONLY_DOCUMENTATION_CHANGED",
                    RelevanceEvidencePriority.COUNTER,
                    -.8,
                    "The comparison contains documentation changes only.",
                    "changed-files",
                    Map.of()));
        }

        FailureClassification classification = context.classification();
        if (classification == FailureClassification.DEPENDENCY_FAILURE) {
            context.changedFiles().stream()
                    .filter(file -> file.file().fileCategory() == FileCategory.DEPENDENCY_MANIFEST
                            || file.file().fileCategory() == FileCategory.DEPENDENCY_LOCKFILE)
                    .forEach(file -> addRelated(
                            evidence,
                            relatedFiles,
                            file,
                            "DEPENDENCY_FILE_CHANGED",
                            .9,
                            "A dependency manifest or lockfile changed for a dependency failure."));
        }
        if (classification == FailureClassification.INFRASTRUCTURE_FAILURE
                || classification == FailureClassification.CONFIGURATION_FAILURE
                || classification == FailureClassification.ENVIRONMENT_FAILURE) {
            context.changedFiles().stream()
                    .filter(file -> file.file().fileCategory() == FileCategory.CI_CONFIGURATION
                            || file.file().fileCategory()
                                    == FileCategory.INFRASTRUCTURE_CONFIGURATION
                            || file.file().fileCategory()
                                    == FileCategory.APPLICATION_CONFIGURATION)
                    .forEach(file -> addRelated(
                            evidence,
                            relatedFiles,
                            file,
                            evidenceCode(file.file().fileCategory()),
                            .8,
                            "A configuration file changed in a category compatible with this failure."));
        }

        if (classification == FailureClassification.BUILD_FAILURE
                || classification == FailureClassification.DEPENDENCY_FAILURE) {
            List<FailureChangeContext.ContextChangedFile> buildFiles = context.changedFiles().stream()
                    .filter(file -> file.file().fileCategory() == FileCategory.BUILD_CONFIGURATION)
                    .toList();
            if (!buildFiles.isEmpty()) score += .7;
            buildFiles.forEach(file -> addRelated(
                    evidence,
                    relatedFiles,
                    file,
                    "BUILD_CONFIGURATION_CHANGED",
                    .7,
                    "Build configuration changed for a build or dependency failure."));
        }
        if (classification == FailureClassification.TEST_FAILURE
                || classification == FailureClassification.INFRASTRUCTURE_FAILURE
                || classification == FailureClassification.CONFIGURATION_FAILURE) {
            List<FailureChangeContext.ContextChangedFile> migrationFiles =
                    context.changedFiles().stream()
                            .filter(file -> file.file().fileCategory()
                                    == FileCategory.DATABASE_MIGRATION)
                            .toList();
            if (!migrationFiles.isEmpty()) score += .65;
            migrationFiles.forEach(file -> addRelated(
                    evidence,
                    relatedFiles,
                    file,
                    "DATABASE_MIGRATION_CHANGED",
                    .65,
                    "A database migration changed in a failure context that can exercise database integration."));
        }

        score += addTestRelationships(context, evidence, relatedFiles);
        ChangeRelevance relevance = classify(score);
        return new DetailedResult(
                relevance,
                confidence(score),
                VERSION,
                summary(relevance),
                List.copyOf(evidence),
                relatedFiles.stream().distinct().toList());
    }

    private double baseScore(
            String failureCategory,
            Collection<FileCategory> files,
            boolean predates,
            boolean rerunPassed) {
        double score = 0;
        if (predates) score -= .7;
        if (rerunPassed) score -= .6;
        if (!files.isEmpty() && files.stream().allMatch(file -> file == FileCategory.DOCUMENTATION)) {
            score -= .8;
        }
        if (failureCategory.equals("DEPENDENCY_FAILURE")
                && files.stream().anyMatch(file -> file == FileCategory.DEPENDENCY_MANIFEST
                        || file == FileCategory.DEPENDENCY_LOCKFILE)) score += .9;
        if ((failureCategory.equals("INFRASTRUCTURE_FAILURE")
                        || failureCategory.equals("CONFIGURATION_FAILURE")
                        || failureCategory.equals("ENVIRONMENT_FAILURE"))
                && files.stream().anyMatch(file -> file == FileCategory.CI_CONFIGURATION
                        || file == FileCategory.INFRASTRUCTURE_CONFIGURATION
                        || file == FileCategory.APPLICATION_CONFIGURATION)) score += .8;
        return score;
    }

    private double addTestRelationships(
            FailureChangeContext context,
            List<ChangeRelevanceEvidence> evidence,
            List<RelatedChangedFile> relatedFiles) {
        if (context.correlatedTest() == null) return 0;
        String testStem = testStem(context.correlatedTest().className());
        if (testStem.isBlank()) return 0;
        double strongest = 0;
        for (FailureChangeContext.ContextChangedFile contextFile : context.changedFiles()) {
            ChangedFile file = contextFile.file();
            if (!file.path().toLowerCase(Locale.ROOT).contains(testStem)) continue;
            double weight = context.correlatedTest().correlationStrength() == CorrelationStrength.EXACT
                    ? .6
                    : .45;
            String code;
            String description;
            if (file.fileCategory() == FileCategory.TEST_SOURCE) {
                code = "FAILING_TEST_FILE_CHANGED";
                description = "The correlated failing test file is present in the change set.";
            } else if (file.fileCategory() == FileCategory.PRODUCTION_SOURCE) {
                code = "RELATED_PRODUCTION_FILE_CHANGED";
                description = "A production source file is name-related to the correlated failing test.";
            } else {
                continue;
            }
            evidence.add(evidence(
                    code,
                    RelevanceEvidencePriority.MEDIUM,
                    weight,
                    description,
                    "structured-test-correlation",
                    Map.of(
                            "path", file.path(),
                            "correlationStrength", context.correlatedTest().correlationStrength().name())));
            relatedFiles.add(new RelatedChangedFile(contextFile.id(), file.path(), code, weight));
            strongest = Math.max(strongest, weight);
        }
        return strongest;
    }

    private void addRelated(
            List<ChangeRelevanceEvidence> evidence,
            List<RelatedChangedFile> relatedFiles,
            FailureChangeContext.ContextChangedFile contextFile,
            String code,
            double weight,
            String description) {
        evidence.add(evidence(
                code,
                RelevanceEvidencePriority.STRONG,
                weight,
                description,
                "changed-files",
                Map.of("path", contextFile.file().path())));
        relatedFiles.add(new RelatedChangedFile(
                contextFile.id(), contextFile.file().path(), code, weight));
    }

    private ChangeRelevanceEvidence evidence(
            String code,
            RelevanceEvidencePriority priority,
            double weight,
            String description,
            String source,
            Map<String, String> metadata) {
        return new ChangeRelevanceEvidence(code, priority, weight, description, source, metadata);
    }

    private String evidenceCode(FileCategory category) {
        return switch (category) {
            case CI_CONFIGURATION -> "CI_CONFIGURATION_CHANGED";
            case INFRASTRUCTURE_CONFIGURATION -> "INFRASTRUCTURE_CONFIGURATION_CHANGED";
            case APPLICATION_CONFIGURATION -> "APPLICATION_CONFIGURATION_CHANGED";
            default -> "CONFIGURATION_CHANGED";
        };
    }

    private String testStem(String className) {
        if (className == null || className.isBlank()) return "";
        String simple = className.substring(className.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return simple.replaceFirst("(tests?|spec|it)$", "");
    }

    private ChangeRelevance classify(double score) {
        if (score >= .8) return ChangeRelevance.RELATED;
        if (score >= .55) return ChangeRelevance.LIKELY_RELATED;
        if (score <= -.7) return ChangeRelevance.UNRELATED;
        if (score <= -.35) return ChangeRelevance.UNLIKELY_RELATED;
        return ChangeRelevance.INDETERMINATE;
    }

    private double confidence(double score) {
        return Math.min(1, Math.abs(score));
    }

    private String summary(ChangeRelevance relevance) {
        return switch (relevance) {
            case RELATED -> "The available evidence strongly relates this failure to the current change.";
            case LIKELY_RELATED -> "The available evidence suggests this failure may relate to the current change.";
            case UNLIKELY_RELATED -> "The available evidence suggests the current change is unlikely to explain this failure.";
            case UNRELATED -> "Strong counter-evidence indicates the current change is unlikely to explain this failure.";
            case INDETERMINATE -> "The available evidence is insufficient or conflicting; change relevance is indeterminate.";
        };
    }

    public record Result(String relevance, double confidence, String version) {}

    public record DetailedResult(
            ChangeRelevance relevance,
            double confidence,
            String version,
            String summary,
            List<ChangeRelevanceEvidence> evidence,
            List<RelatedChangedFile> relatedFiles) {}
}
