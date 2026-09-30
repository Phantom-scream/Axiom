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
    private final com.axiom.config.RerunHistoryProperties properties;

    public RerunAnalysisService(JdbcTemplate jdbc) {
        this(jdbc, new com.axiom.config.RerunHistoryProperties(null));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RerunAnalysisService(JdbcTemplate jdbc, com.axiom.config.RerunHistoryProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    public List<RerunTransition> transitions(String stableTestId) {
        List<Execution> executions = jdbc.query(
                """
                with recent as (
                    select t.*, pr.external_run_id, pr.attempt, pr.commit_sha
                    from test_case_executions t
                    join pipeline_runs pr on pr.id = t.pipeline_run_id
                    where t.stable_test_id = ?
                    order by coalesce(pr.finished_at, pr.started_at, pr.created_at) desc,
                             pr.external_run_id desc, pr.attempt desc, t.created_at desc, t.id
                    limit ?
                )
                select distinct on (external_run_id, attempt)
                       pipeline_run_id, stable_test_id, external_run_id, attempt,
                       commit_sha, status, failure_fingerprint
                from recent
                order by external_run_id, attempt, created_at desc, id
                """,
                (rs, row) -> new Execution(
                        rs.getObject("pipeline_run_id", UUID.class),
                        rs.getString("stable_test_id").trim(),
                        rs.getLong("external_run_id"),
                        rs.getInt("attempt"),
                        rs.getString("commit_sha"),
                        TestStatus.valueOf(rs.getString("status")),
                        rs.getString("failure_fingerprint")),
                stableTestId, properties.effectiveMaxExecutions());
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
            boolean sameCommit = from.commitSha() != null && !from.commitSha().isBlank()
                    && Objects.equals(from.commitSha(), to.commitSha());
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

    /** Batch historical view restricted to an already bounded repository run window. */
    public java.util.Map<String,List<RerunTransition>> transitionsForRuns(java.util.Set<String> stableIds,List<UUID> runIds) {
        if(stableIds.isEmpty() || runIds.isEmpty()) return java.util.Map.of();
        if(stableIds.size()>100 || runIds.size()>10000) throw new IllegalArgumentException("Rerun batch exceeds the bounded history window.");
        String tests=String.join(",",java.util.Collections.nCopies(stableIds.size(),"?"));
        String runs=String.join(",",java.util.Collections.nCopies(runIds.size(),"?"));
        var args=new ArrayList<Object>();args.addAll(stableIds);args.addAll(runIds);
        var executions=jdbc.query("""
                select distinct on (t.stable_test_id,pr.external_run_id,pr.attempt)
                  t.pipeline_run_id,t.stable_test_id,pr.external_run_id,pr.attempt,pr.commit_sha,t.status,t.failure_fingerprint
                from test_case_executions t join pipeline_runs pr on pr.id=t.pipeline_run_id
                where t.stable_test_id in (%s) and pr.id in (%s)
                order by t.stable_test_id,pr.external_run_id,pr.attempt,t.created_at desc,t.id
                """.formatted(tests,runs),(rs,row)->new Execution(rs.getObject("pipeline_run_id",UUID.class),rs.getString("stable_test_id").trim(),rs.getLong("external_run_id"),rs.getInt("attempt"),rs.getString("commit_sha"),TestStatus.valueOf(rs.getString("status")),rs.getString("failure_fingerprint")),args.toArray());
        var grouped=executions.stream().collect(java.util.stream.Collectors.groupingBy(Execution::stableTestId));
        var result=new java.util.HashMap<String,List<RerunTransition>>(); grouped.forEach((id,values)->result.put(id,analyze(values)));return java.util.Map.copyOf(result);
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
