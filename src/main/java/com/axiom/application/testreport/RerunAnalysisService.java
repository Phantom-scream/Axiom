package com.axiom.application.testreport;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.test.RerunTransition;
import com.axiom.domain.test.RerunTransitionType;
import com.axiom.domain.test.TestStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RerunAnalysisService {
    private final JdbcTemplate jdbc;

    public RerunAnalysisService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RerunTransition> transitions(String stableTestId) {
        List<Execution> executions = jdbc.query(
                """
                select distinct on (pr.external_run_id, pr.attempt)
                       t.pipeline_run_id, t.stable_test_id, pr.external_run_id, pr.attempt,
                       pr.commit_sha, t.status, t.failure_fingerprint
                from test_case_executions t
                join pipeline_runs pr on pr.id = t.pipeline_run_id
                where t.stable_test_id = ?
                order by pr.external_run_id, pr.attempt, t.created_at desc, t.id
                """,
                (rs, row) -> new Execution(
                        rs.getObject("pipeline_run_id", UUID.class),
                        rs.getString("stable_test_id").trim(),
                        rs.getLong("external_run_id"),
                        rs.getInt("attempt"),
                        rs.getString("commit_sha"),
                        TestStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_fingerprint")),
                stableTestId);
        if (executions.isEmpty()) {
            throw new ResourceNotFoundException("Stable test " + stableTestId + " was not found.");
        }
        return analyze(executions);
    }

    List<RerunTransition> analyze(List<Execution> executions) {
        List<Execution> ordered = executions.stream()
                .sorted(Comparator.comparingLong(Execution::externalRunId)
                        .thenComparingInt(Execution::runAttempt))
                .toList();
        List<RerunTransition> transitions = new ArrayList<>();
        for (int index = 1; index < ordered.size(); index++) {
            Execution from = ordered.get(index - 1);
            Execution to = ordered.get(index);
            if (!from.stableTestId().equals(to.stableTestId())
                    || from.externalRunId() != to.externalRunId()
                    || from.runAttempt() == to.runAttempt()) {
                continue;
            }
            boolean sameCommit = Objects.equals(from.commitSha(), to.commitSha());
            transitions.add(new RerunTransition(
                    from.stableTestId(),
                    from.externalRunId(),
                    from.pipelineRunId(),
                    to.pipelineRunId(),
                    from.runAttempt(),
                    to.runAttempt(),
                    from.status(),
                    to.status(),
                    from.failureFingerprint(),
                    to.failureFingerprint(),
                    sameCommit,
                    transitionType(from, to, sameCommit)));
        }
        return List.copyOf(transitions);
    }

    private RerunTransitionType transitionType(Execution from, Execution to, boolean sameCommit) {
        if (!sameCommit) return RerunTransitionType.UNKNOWN;
        if (from.status() == TestStatus.FAILED && to.status() == TestStatus.PASSED) {
            return RerunTransitionType.FAIL_TO_PASS;
        }
        if (from.status() == TestStatus.ERROR && to.status() == TestStatus.PASSED) {
            return RerunTransitionType.ERROR_TO_PASS;
        }
        if (from.status() == TestStatus.FAILED && to.status() == TestStatus.FAILED) {
            return from.failureFingerprint() != null
                            && from.failureFingerprint().equals(to.failureFingerprint())
                    ? RerunTransitionType.FAIL_TO_FAIL
                    : RerunTransitionType.FAIL_TO_DIFFERENT_FAILURE;
        }
        if (from.status() == TestStatus.ERROR
                && to.status() == TestStatus.ERROR
                && from.failureFingerprint() != null
                && from.failureFingerprint().equals(to.failureFingerprint())) {
            return RerunTransitionType.ERROR_TO_ERROR;
        }
        if (from.status() == TestStatus.PASSED && to.status() == TestStatus.FAILED) {
            return RerunTransitionType.PASS_TO_FAIL;
        }
        if (from.status() == TestStatus.PASSED && to.status() == TestStatus.PASSED) {
            return RerunTransitionType.PASS_TO_PASS;
        }
        return RerunTransitionType.UNKNOWN;
    }

    record Execution(
            UUID pipelineRunId,
            String stableTestId,
            long externalRunId,
            int runAttempt,
            String commitSha,
            TestStatus status,
            String failureFingerprint) {}
}
