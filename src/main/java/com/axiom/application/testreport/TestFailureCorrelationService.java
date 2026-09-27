package com.axiom.application.testreport;

import com.axiom.api.dto.TestCorrelationResponse;
import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.test.CorrelationStrength;
import com.axiom.domain.test.TestStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestFailureCorrelationService {
    private final JdbcTemplate jdbc;

    public TestFailureCorrelationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public TestCorrelationResponse correlate(UUID pipelineRunId) {
        if (!runExists(pipelineRunId)) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }

        List<FailureCandidate> failures = jdbc.query(
                """
                select id, pipeline_job_id, fingerprint, normalized_message, exception_type
                from failure_events
                where pipeline_run_id = ?
                """,
                (rs, row) -> new FailureCandidate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("pipeline_job_id", UUID.class),
                        rs.getString("fingerprint"),
                        rs.getString("normalized_message"),
                        rs.getString("exception_type")),
                pipelineRunId);

        List<TestExecution> executions = jdbc.query(
                """
                select t.id, t.status, t.failure_fingerprint, t.normalized_failure_message,
                       t.failure_type, tr.pipeline_job_id
                from test_case_executions t
                join test_suites ts on ts.id = t.test_suite_id
                join test_reports tr on tr.id = ts.test_report_id
                where t.pipeline_run_id = ? and t.status in ('FAILED', 'ERROR')
                order by t.created_at, t.id
                """,
                (rs, row) -> new TestExecution(
                        rs.getObject("id", UUID.class),
                        TestStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_fingerprint"),
                        rs.getString("normalized_failure_message"),
                        rs.getString("failure_type"),
                        rs.getObject("pipeline_job_id", UUID.class)),
                pipelineRunId);

        int exact = 0;
        int strong = 0;
        for (TestExecution execution : executions) {
            Match match = selectMatch(execution, failures);
            jdbc.update(
                    "update test_case_executions set correlated_failure_event_id=?, correlation_strength=? where id=?",
                    match.failureEventId(),
                    match.strength().name(),
                    execution.id());
            if (match.strength() == CorrelationStrength.EXACT) exact++;
            if (match.strength() == CorrelationStrength.STRONG) strong++;
        }
        return new TestCorrelationResponse(
                pipelineRunId, executions.size(), exact, strong, executions.size() - exact - strong);
    }

    Match selectMatch(TestExecution execution, List<FailureCandidate> failures) {
        if (execution.status() != TestStatus.FAILED && execution.status() != TestStatus.ERROR) {
            return Match.none();
        }

        if (hasText(execution.fingerprint())) {
            List<FailureCandidate> exact = failures.stream()
                    .filter(candidate -> execution.fingerprint().equals(candidate.fingerprint()))
                    .filter(candidate -> compatibleJob(execution.pipelineJobId(), candidate.pipelineJobId()))
                    .toList();
            if (exact.size() == 1) {
                return new Match(exact.getFirst().id(), CorrelationStrength.EXACT);
            }
            if (exact.size() > 1) {
                return Match.none();
            }
        }

        List<ScoredCandidate> strong = failures.stream()
                .filter(candidate -> compatibleJob(execution.pipelineJobId(), candidate.pipelineJobId()))
                .map(candidate -> new ScoredCandidate(candidate, strongScore(execution, candidate)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingInt(ScoredCandidate::score).reversed())
                .toList();
        if (strong.isEmpty()) {
            return Match.none();
        }
        int bestScore = strong.getFirst().score();
        if (strong.stream().filter(candidate -> candidate.score() == bestScore).count() != 1) {
            return Match.none();
        }
        return new Match(strong.getFirst().candidate().id(), CorrelationStrength.STRONG);
    }

    private int strongScore(TestExecution execution, FailureCandidate candidate) {
        if (!compatibleType(execution.failureType(), candidate.exceptionType())) {
            return 0;
        }
        String testMessage = canonical(execution.normalizedMessage());
        String eventMessage = canonical(candidate.normalizedMessage());
        if (testMessage.isEmpty() || eventMessage.isEmpty()) {
            return 0;
        }
        int score;
        if (testMessage.equals(eventMessage)) {
            score = 6;
        } else if (Math.min(testMessage.length(), eventMessage.length()) >= 16
                && (testMessage.contains(eventMessage) || eventMessage.contains(testMessage))) {
            score = 5;
        } else {
            return 0;
        }
        if (execution.pipelineJobId() != null && candidate.pipelineJobId() != null) score++;
        return score;
    }

    private boolean runExists(UUID pipelineRunId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pipeline_runs where id=?)", Boolean.class, pipelineRunId));
    }

    private boolean compatibleJob(UUID testJobId, UUID failureJobId) {
        return testJobId == null || failureJobId == null || testJobId.equals(failureJobId);
    }

    private boolean compatibleType(String testType, String eventType) {
        return hasText(testType)
                && hasText(eventType)
                && simpleType(testType).equalsIgnoreCase(simpleType(eventType));
    }

    private String simpleType(String value) {
        int separator = Math.max(value.lastIndexOf('.'), value.lastIndexOf('$'));
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private String canonical(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record TestExecution(
            UUID id,
            TestStatus status,
            String fingerprint,
            String normalizedMessage,
            String failureType,
            UUID pipelineJobId) {}

    record FailureCandidate(
            UUID id,
            UUID pipelineJobId,
            String fingerprint,
            String normalizedMessage,
            String exceptionType) {}

    record Match(UUID failureEventId, CorrelationStrength strength) {
        static Match none() {
            return new Match(null, CorrelationStrength.NONE);
        }
    }

    private record ScoredCandidate(FailureCandidate candidate, int score) {}
}
