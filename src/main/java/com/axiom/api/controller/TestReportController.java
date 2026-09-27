package com.axiom.api.controller;

import com.axiom.api.dto.TestCorrelationResponse;
import com.axiom.application.testreport.TestFailureCorrelationService;
import com.axiom.application.testreport.TestReportIngestionService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TestReportController {
    private final TestReportIngestionService ingestion;
    private final TestFailureCorrelationService correlation;
    private final JdbcTemplate jdbc;

    public TestReportController(
            TestReportIngestionService ingestion,
            TestFailureCorrelationService correlation,
            JdbcTemplate jdbc) {
        this.ingestion = ingestion;
        this.correlation = correlation;
        this.jdbc = jdbc;
    }

    @PostMapping(
            value = "/pipeline-runs/{id}/test-reports",
            consumes = {"application/xml", "text/xml"})
    @ResponseStatus(HttpStatus.CREATED)
    public Object ingest(
            @PathVariable UUID id,
            @RequestParam String sourceName,
            @RequestParam(required = false) String frameworkHint,
            @RequestBody byte[] xml) {
        return ingestion.ingest(id, sourceName, xml, frameworkHint);
    }

    @PostMapping("/pipeline-runs/{id}/tests/correlate")
    public TestCorrelationResponse correlate(@PathVariable UUID id) {
        return correlation.correlate(id);
    }

    @GetMapping("/pipeline-runs/{id}/tests")
    public Object tests(@PathVariable UUID id) {
        return jdbc.queryForList(
                "select total_tests,passed,failed,errors,skipped from test_reports where pipeline_run_id=?",
                id);
    }

    @GetMapping("/pipeline-runs/{id}/tests/failures")
    public Object failures(@PathVariable UUID id) {
        return jdbc.queryForList(
                """
                select * from test_case_executions
                where pipeline_run_id=? and status in ('FAILED','ERROR')
                """,
                id);
    }

    @GetMapping("/tests/{stable}/executions")
    public Object history(@PathVariable String stable) {
        return jdbc.queryForList(
                "select * from test_case_executions where stable_test_id=?", stable);
    }
}
