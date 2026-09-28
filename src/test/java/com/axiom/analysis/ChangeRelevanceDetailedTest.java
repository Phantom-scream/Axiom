package com.axiom.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.analysis.correlation.ChangeRelevanceService;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.relevance.FailureChangeContext;
import com.axiom.domain.test.CorrelationStrength;
import com.axiom.domain.test.RerunTransition;
import com.axiom.domain.test.RerunTransitionType;
import com.axiom.domain.test.TestStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChangeRelevanceDetailedTest {
    private final ChangeRelevanceService service = new ChangeRelevanceService();

    @Test
    void dependencyChangeProducesSpecificPositiveEvidence() {
        var result = service.analyze(context(
                FailureClassification.DEPENDENCY_FAILURE,
                List.of(file("pom.xml", FileCategory.DEPENDENCY_MANIFEST)),
                null,
                0,
                List.of()));

        assertThat(result.relevance()).isEqualTo(ChangeRelevance.RELATED);
        assertThat(result.evidence()).extracting("code").contains("DEPENDENCY_FILE_CHANGED");
        assertThat(result.relatedFiles()).singleElement().extracting("path").isEqualTo("pom.xml");
    }

    @Test
    void documentationHistoryAndUnchangedRerunProvideCounterEvidence() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        var transition = new RerunTransition(
                "stable",
                42,
                from,
                to,
                1,
                2,
                TestStatus.FAILED,
                TestStatus.PASSED,
                "fp",
                null,
                true,
                RerunTransitionType.FAIL_TO_PASS);
        var result = service.analyze(context(
                FailureClassification.INFRASTRUCTURE_FAILURE,
                List.of(file("README.md", FileCategory.DOCUMENTATION)),
                null,
                3,
                List.of(transition)));

        assertThat(result.relevance()).isEqualTo(ChangeRelevance.UNRELATED);
        assertThat(result.evidence())
                .extracting("code")
                .contains(
                        "ONLY_DOCUMENTATION_CHANGED",
                        "SAME_FINGERPRINT_PREDATES_CHANGE",
                        "UNCHANGED_RERUN_FAIL_TO_PASS");
    }

    @Test
    void exactCorrelatedTestRelatesNameMatchedProductionSource() {
        var correlated = new FailureChangeContext.CorrelatedTest(
                UUID.randomUUID(),
                "stable",
                "com.example.PaymentServiceTest",
                "rejectsInvalidPayment",
                "payments",
                CorrelationStrength.EXACT);
        var result = service.analyze(context(
                FailureClassification.TEST_FAILURE,
                List.of(file(
                        "src/main/java/com/example/PaymentService.java",
                        FileCategory.PRODUCTION_SOURCE)),
                correlated,
                0,
                List.of()));

        assertThat(result.relevance()).isEqualTo(ChangeRelevance.LIKELY_RELATED);
        assertThat(result.evidence())
                .extracting("code")
                .contains("RELATED_PRODUCTION_FILE_CHANGED");
    }

    @Test
    void unsupportedPathAndMissingOptionalEvidenceRemainIndeterminate() {
        var result = service.analyze(context(
                FailureClassification.UNKNOWN,
                List.of(file("misc/unrecognized.xyz", FileCategory.UNKNOWN)),
                null,
                0,
                List.of()));

        assertThat(result.relevance()).isEqualTo(ChangeRelevance.INDETERMINATE);
    }

    @Test
    void differentShaRerunDoesNotProduceUnchangedRerunEvidence() {
        var transition = new RerunTransition(
                "stable",
                43,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                2,
                TestStatus.FAILED,
                TestStatus.PASSED,
                "fp",
                null,
                false,
                RerunTransitionType.UNKNOWN);

        var result = service.analyze(context(
                FailureClassification.UNKNOWN,
                List.of(file("misc/unrecognized.xyz", FileCategory.UNKNOWN)),
                null,
                0,
                List.of(transition)));

        assertThat(result.evidence())
                .extracting("code")
                .doesNotContain("UNCHANGED_RERUN_FAIL_TO_PASS");
        assertThat(result.relevance()).isEqualTo(ChangeRelevance.INDETERMINATE);
    }

    private FailureChangeContext context(
            FailureClassification classification,
            List<FailureChangeContext.ContextChangedFile> files,
            FailureChangeContext.CorrelatedTest correlatedTest,
            int priorOccurrences,
            List<RerunTransition> reruns) {
        return new FailureChangeContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "fingerprint",
                "message",
                "Exception",
                classification,
                "base",
                "head",
                files,
                correlatedTest,
                null,
                new FailureChangeContext.FingerprintHistory(priorOccurrences, List.of()),
                reruns);
    }

    private FailureChangeContext.ContextChangedFile file(String path, FileCategory category) {
        return new FailureChangeContext.ContextChangedFile(
                UUID.randomUUID(),
                new ChangedFile(
                        path,
                        null,
                        ChangeType.MODIFIED,
                        category,
                        null,
                        1,
                        0,
                        1));
    }
}
