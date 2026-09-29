package com.axiom.application.publication;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.triage.ActionType;
import com.axiom.domain.triage.FailureRole;
import com.axiom.domain.triage.FailureTriageEntry;
import com.axiom.domain.triage.PipelineTriageResult;
import com.axiom.domain.triage.RecommendedAction;
import com.axiom.domain.triage.RerunRecommendation;
import com.axiom.domain.triage.TriageEvidence;
import com.axiom.domain.triage.TriageEvidencePriority;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitHubPrCommentRendererTest {
    private final GitHubPrCommentRenderer renderer =
            new GitHubPrCommentRenderer(new GitHubCheckRenderer());

    @Test
    void rendersConciseMarkedEscapedEvidenceAndActions() {
        UUID failureId = UUID.randomUUID();
        var result = new PipelineTriageResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "COMPLETED",
                failureId,
                "abc`123",
                FailureRole.PRIMARY,
                FailureClassification.TEST_FAILURE,
                ChangeRelevance.LIKELY_RELATED,
                RerunRecommendation.NOT_RECOMMENDED,
                .86,
                "A <test> failure appears relevant, not proven causal.",
                "triage-v1",
                Instant.now(),
                List.of(new RecommendedAction(
                        1,
                        ActionType.INSPECT_TEST,
                        "Inspect *the test*.",
                        "The assertion is the primary signal.")),
                List.of(new FailureTriageEntry(
                        failureId,
                        "abc`123",
                        FailureClassification.TEST_FAILURE,
                        FailureRole.PRIMARY,
                        .88,
                        ChangeRelevance.LIKELY_RELATED,
                        2,
                        1,
                        null,
                        false,
                        true)),
                List.of(new TriageEvidence(
                        failureId,
                        "TEST_SIGNAL",
                        TriageEvidencePriority.STRONG,
                        .8,
                        "Assertion [signal] matched.",
                        Map.of())));

        String body = renderer.render(result);

        assertThat(body).startsWith(GitHubPrCommentRenderer.MARKER);
        assertThat(body)
                .contains(
                        "## Axiom CI Intelligence",
                        "test failure",
                        "likely related",
                        "not recommended",
                        "Inspect \\*the test\\*",
                        "Assertion \\[signal\\] matched")
                .doesNotContain("A <test>", "raw CI log", "stack trace");
        assertThat(body.length()).isLessThanOrEqualTo(60_000);
    }

    @Test
    void boundsLargePersistedSummary() {
        var result = new PipelineTriageResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INSUFFICIENT_EVIDENCE",
                null,
                null,
                FailureRole.UNKNOWN,
                null,
                null,
                RerunRecommendation.INSUFFICIENT_EVIDENCE,
                0,
                "x".repeat(100_000),
                "triage-v1",
                Instant.now(),
                List.of(),
                List.of(),
                List.of());

        assertThat(renderer.render(result).length()).isLessThanOrEqualTo(60_000);
    }
}
