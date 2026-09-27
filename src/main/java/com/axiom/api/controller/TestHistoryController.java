package com.axiom.api.controller;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.api.dto.RerunTransitionResponse;
import com.axiom.application.testreport.RerunAnalysisService;
import com.axiom.application.testreport.TestStabilityService;
import com.axiom.domain.test.TestStatus;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tests")
public class TestHistoryController {
    private final JdbcTemplate jdbc;
    private final TestStabilityService stability;
    private final RerunAnalysisService reruns;

    public TestHistoryController(
            JdbcTemplate jdbc, TestStabilityService stability, RerunAnalysisService reruns) {
        this.jdbc = jdbc;
        this.stability = stability;
        this.reruns = reruns;
    }

    @GetMapping("/{id}/history")
    public Object history(@PathVariable String id) {
        return jdbc.queryForList(
                """
                select t.status, t.failure_fingerprint, pr.external_run_id, pr.attempt,
                       pr.commit_sha, pr.finished_at
                from test_case_executions t
                join pipeline_runs pr on pr.id=t.pipeline_run_id
                where t.stable_test_id=?
                order by pr.finished_at, pr.attempt
                """,
                id);
    }

    @GetMapping("/{id}/fingerprints")
    public Object fingerprints(@PathVariable String id) {
        return jdbc.queryForList(
                """
                select failure_fingerprint, count(*) occurrences
                from test_case_executions
                where stable_test_id=? and failure_fingerprint is not null
                group by failure_fingerprint
                """,
                id);
    }

    @GetMapping("/{id}/reruns")
    public List<RerunTransitionResponse> reruns(@PathVariable String id) {
        return reruns.transitions(id).stream().map(RerunTransitionResponse::from).toList();
    }

    @GetMapping("/{id}/stability")
    public Object result(@PathVariable String id) {
        var rows = jdbc.queryForList(
                "select status,failure_fingerprint from test_case_executions where stable_test_id=?",
                id);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Stable test " + id + " was not found.");
        }
        List<TestStatus> statuses = rows.stream()
                .map(row -> TestStatus.valueOf((String) row.get("status")))
                .toList();
        int repeated = (int) rows.stream()
                .filter(row -> row.get("failure_fingerprint") != null)
                .count();
        return stability.analyze(id, statuses, repeated);
    }
}
