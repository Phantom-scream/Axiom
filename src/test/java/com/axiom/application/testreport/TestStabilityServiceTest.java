package com.axiom.application.testreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiom.domain.test.RerunTransition;
import com.axiom.domain.test.RerunTransitionType;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.test.TestStatus;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestStabilityServiceTest {
    private final RerunAnalysisService reruns = mock(RerunAnalysisService.class);
    private final TestStabilityService service = new TestStabilityService(reruns);

    @Test
    void conservativeClassesRemainUnchanged() {
        assertThat(service.classify(List.of(TestStatus.FAILED), 0, 0).classification())
                .isEqualTo(TestStabilityClass.INSUFFICIENT_HISTORY);
        assertThat(service.classify(Collections.nCopies(20, TestStatus.PASSED), 0, 0).classification())
                .isEqualTo(TestStabilityClass.STABLE);
        assertThat(service.classify(
                                Arrays.asList(
                                        TestStatus.PASSED,
                                        TestStatus.PASSED,
                                        TestStatus.PASSED,
                                        TestStatus.PASSED,
                                        TestStatus.FAILED),
                                1,
                                0)
                        .classification())
                .isEqualTo(TestStabilityClass.SUSPECTED_FLAKY);
    }

    @Test
    void failToPassEvidenceComesFromRerunAnalysis() {
        String stableId = "stable-id";
        when(reruns.transitions(stableId))
                .thenReturn(List.of(new RerunTransition(
                        stableId,
                        42,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        2,
                        TestStatus.FAILED,
                        TestStatus.PASSED,
                        "fingerprint",
                        null,
                        true,
                        RerunTransitionType.FAIL_TO_PASS)));

        var result = service.analyze(
                stableId,
                List.of(
                        TestStatus.FAILED,
                        TestStatus.PASSED,
                        TestStatus.PASSED,
                        TestStatus.PASSED,
                        TestStatus.PASSED),
                1);

        assertThat(result.classification()).isEqualTo(TestStabilityClass.SUSPECTED_FLAKY);
        assertThat(result.failToPass()).isEqualTo(1);
        verify(reruns).transitions(stableId);
    }
}
