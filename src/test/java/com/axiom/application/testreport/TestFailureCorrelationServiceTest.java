package com.axiom.application.testreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.axiom.domain.test.CorrelationStrength;
import com.axiom.domain.test.TestStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TestFailureCorrelationServiceTest {
    private final TestFailureCorrelationService service =
            new TestFailureCorrelationService(mock(JdbcTemplate.class));

    @Test
    void exactFingerprintMatchWins() {
        UUID failureId = UUID.randomUUID();
        var match = service.selectMatch(
                execution(TestStatus.FAILED, "fingerprint", "message", "AssertionError", null),
                List.of(failure(failureId, "fingerprint", "different", "OtherError", null)));

        assertThat(match.failureEventId()).isEqualTo(failureId);
        assertThat(match.strength()).isEqualTo(CorrelationStrength.EXACT);
    }

    @Test
    void normalizedMessageTypeAndJobProduceStrongMatch() {
        UUID jobId = UUID.randomUUID();
        UUID failureId = UUID.randomUUID();
        var match = service.selectMatch(
                execution(
                        TestStatus.ERROR,
                        null,
                        "Connection refused by database",
                        "java.net.ConnectException",
                        jobId),
                List.of(failure(
                        failureId,
                        "other",
                        "Connection refused by database",
                        "ConnectException",
                        jobId)));

        assertThat(match.failureEventId()).isEqualTo(failureId);
        assertThat(match.strength()).isEqualTo(CorrelationStrength.STRONG);
    }

    @Test
    void equallyPlausibleCandidatesRemainUncorrelated() {
        var execution = execution(
                TestStatus.ERROR, null, "Connection refused by database", "ConnectException", null);
        var match = service.selectMatch(
                execution,
                List.of(
                        failure(
                                UUID.randomUUID(),
                                "one",
                                "Connection refused by database",
                                "ConnectException",
                                null),
                        failure(
                                UUID.randomUUID(),
                                "two",
                                "Connection refused by database",
                                "ConnectException",
                                null)));

        assertThat(match.strength()).isEqualTo(CorrelationStrength.NONE);
        assertThat(match.failureEventId()).isNull();
    }

    @Test
    void passingAndSkippedExecutionsNeverCorrelate() {
        var candidate = failure(
                UUID.randomUUID(), "fingerprint", "same message long enough", "AssertionError", null);

        assertThat(service.selectMatch(
                                execution(
                                        TestStatus.PASSED,
                                        "fingerprint",
                                        "same message long enough",
                                        "AssertionError",
                                        null),
                                List.of(candidate))
                        .strength())
                .isEqualTo(CorrelationStrength.NONE);
        assertThat(service.selectMatch(
                                execution(
                                        TestStatus.SKIPPED,
                                        "fingerprint",
                                        "same message long enough",
                                        "AssertionError",
                                        null),
                                List.of(candidate))
                        .strength())
                .isEqualTo(CorrelationStrength.NONE);
    }

    @Test
    void failedExecutionWithoutCandidateRemainsUncorrelated() {
        assertThat(service.selectMatch(
                                execution(
                                        TestStatus.FAILED,
                                        null,
                                        "assertion output",
                                        "AssertionError",
                                        null),
                                List.of())
                        .strength())
                .isEqualTo(CorrelationStrength.NONE);
    }

    private TestFailureCorrelationService.TestExecution execution(
            TestStatus status,
            String fingerprint,
            String message,
            String failureType,
            UUID jobId) {
        return new TestFailureCorrelationService.TestExecution(
                UUID.randomUUID(), status, fingerprint, message, failureType, jobId);
    }

    private TestFailureCorrelationService.FailureCandidate failure(
            UUID id, String fingerprint, String message, String type, UUID jobId) {
        return new TestFailureCorrelationService.FailureCandidate(
                id, jobId, fingerprint, message, type);
    }
}
