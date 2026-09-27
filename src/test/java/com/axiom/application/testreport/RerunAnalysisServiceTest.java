package com.axiom.application.testreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.axiom.domain.test.RerunTransitionType;
import com.axiom.domain.test.TestStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RerunAnalysisServiceTest {
    private final RerunAnalysisService service = new RerunAnalysisService(mock(JdbcTemplate.class));

    @Test
    void classifiesSupportedTransitions() {
        assertTransition(TestStatus.FAILED, TestStatus.PASSED, "a", null, RerunTransitionType.FAIL_TO_PASS);
        assertTransition(TestStatus.ERROR, TestStatus.PASSED, "a", null, RerunTransitionType.ERROR_TO_PASS);
        assertTransition(TestStatus.FAILED, TestStatus.FAILED, "a", "a", RerunTransitionType.FAIL_TO_FAIL);
        assertTransition(TestStatus.ERROR, TestStatus.ERROR, "a", "a", RerunTransitionType.ERROR_TO_ERROR);
        assertTransition(
                TestStatus.FAILED,
                TestStatus.FAILED,
                "a",
                "b",
                RerunTransitionType.FAIL_TO_DIFFERENT_FAILURE);
        assertTransition(TestStatus.PASSED, TestStatus.FAILED, null, "a", RerunTransitionType.PASS_TO_FAIL);
        assertTransition(TestStatus.PASSED, TestStatus.PASSED, null, null, RerunTransitionType.PASS_TO_PASS);
    }

    @Test
    void differentCommitIsNotUnchangedRerunEvidence() {
        var transition = service.analyze(List.of(
                        execution(1, 1, "sha-one", TestStatus.FAILED, "a"),
                        execution(1, 2, "sha-two", TestStatus.PASSED, null)))
                .getFirst();

        assertThat(transition.sameCommitSha()).isFalse();
        assertThat(transition.transitionType()).isEqualTo(RerunTransitionType.UNKNOWN);
    }

    @Test
    void differentExternalRunsAreNotComparedAsAttempts() {
        assertThat(service.analyze(List.of(
                        execution(1, 1, "sha", TestStatus.FAILED, "a"),
                        execution(2, 2, "sha", TestStatus.PASSED, null))))
                .isEmpty();
    }

    @Test
    void runAttemptOrderingWinsOverInputOrder() {
        var transition = service.analyze(List.of(
                        execution(1, 2, "sha", TestStatus.PASSED, null),
                        execution(1, 1, "sha", TestStatus.FAILED, "a")))
                .getFirst();

        assertThat(transition.fromAttempt()).isEqualTo(1);
        assertThat(transition.toAttempt()).isEqualTo(2);
        assertThat(transition.transitionType()).isEqualTo(RerunTransitionType.FAIL_TO_PASS);
    }

    private void assertTransition(
            TestStatus from,
            TestStatus to,
            String fromFingerprint,
            String toFingerprint,
            RerunTransitionType expected) {
        var transition = service.analyze(List.of(
                        execution(1, 1, "sha", from, fromFingerprint),
                        execution(1, 2, "sha", to, toFingerprint)))
                .getFirst();
        assertThat(transition.transitionType()).isEqualTo(expected);
        assertThat(transition.sameCommitSha()).isTrue();
    }

    private RerunAnalysisService.Execution execution(
            long externalRunId,
            int attempt,
            String sha,
            TestStatus status,
            String fingerprint) {
        return new RerunAnalysisService.Execution(
                UUID.randomUUID(), "stable-id", externalRunId, attempt, sha, status, fingerprint);
    }
}
