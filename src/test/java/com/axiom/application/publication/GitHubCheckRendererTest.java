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

class GitHubCheckRendererTest {
    private final GitHubCheckRenderer renderer = new GitHubCheckRenderer();

    @Test
    void rendersBoundedEscapedTriageWithoutRawLogs() {
        UUID failureId = UUID.randomUUID();
        var triage = new PipelineTriageResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "COMPLETED",
                failureId,
                "abc`123",
                FailureRole.PRIMARY,
                FailureClassification.INFRASTRUCTURE_FAILURE,
                ChangeRelevance.UNLIKELY_RELATED,
                RerunRecommendation.RECOMMENDED,
                .91,
                "Service <database> appears unavailable.",
                "triage-v1",
                Instant.parse("2026-09-28T12:00:00Z"),
                List.of(new RecommendedAction(
                        1,
                        ActionType.RERUN_PIPELINE,
                        "Rerun *once*.",
                        "An unchanged rerun passed.")),
                List.of(new FailureTriageEntry(
                        failureId,
                        "abc`123",
                        FailureClassification.INFRASTRUCTURE_FAILURE,
                        FailureRole.PRIMARY,
                        .94,
                        ChangeRelevance.UNLIKELY_RELATED,
                        8,
                        1,
                        null,
                        true,
                        false)),
                List.of(new TriageEvidence(
                        failureId,
                        "UNCHANGED_RERUN_PASSED",
                        TriageEvidencePriority.STRONG,
                        .9,
                        "Same SHA passed on rerun [evidence].",
                        Map.of())));

        var output = renderer.render(triage);

        assertThat(output.title()).contains("infrastructure failure");
        assertThat(output.summary())
                .contains("unlikely related", "Recommended actions", "Rerun \\*once\\*")
                .doesNotContain("Service <database>")
                .doesNotContain("raw CI log");
        assertThat(output.summary().length()).isLessThanOrEqualTo(8_000);
        assertThat(output.text()).contains("historical occurrences 8", "unchanged-rerun pass observed");
        assertThat(output.text().length()).isLessThanOrEqualTo(60_000);
    }

    @Test
    void rendersInsufficientEvidenceConservatively() {
        var triage = new PipelineTriageResult(
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
                "There is not enough evidence.",
                "triage-v1",
                Instant.now(),
                List.of(),
                List.of(),
                List.of());

        var output = renderer.render(triage);

        assertThat(output.title()).isEqualTo("Axiom: insufficient evidence");
        assertThat(output.summary()).contains("No supported primary failure");
    }
}
