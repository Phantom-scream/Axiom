package com.axiom.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.testreport.TestFailureCorrelationService;
import com.axiom.application.testreport.TestReportIngestionService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TestFailureCorrelationIntegrationTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestFailureCorrelationService correlation;
    @Autowired private TestReportIngestionService ingestion;

    @Test
    void exactCorrelationPersistsAndIsIdempotent() {
        UUID runId = pipelineRun(7001, 1, "exact-sha");
        UUID failureId = failure(
                runId, "f".repeat(64), "expected value", "AssertionError");
        UUID executionId = execution(
                runId,
                "a".repeat(64),
                "FAILED",
                "f".repeat(64),
                "expected value",
                "AssertionError");

        var first = correlation.correlate(runId);
        var second = correlation.correlate(runId);

        assertThat(first.exactMatches()).isEqualTo(1);
        assertThat(second.exactMatches()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select correlated_failure_event_id from test_case_executions where id=?",
                        UUID.class,
                        executionId))
                .isEqualTo(failureId);
        assertThat(jdbc.queryForObject(
                        "select correlation_strength from test_case_executions where id=?",
                        String.class,
                        executionId))
                .isEqualTo("EXACT");
    }

    @Test
    void correlationCanBeRepeatedAfterFailureEventsArriveLater() {
        UUID runId = pipelineRun(7002, 1, "late-sha");
        UUID executionId = execution(
                runId,
                "b".repeat(64),
                "ERROR",
                null,
                "Connection refused by database",
                "ConnectException");

        assertThat(correlation.correlate(runId).uncorrelated()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select correlation_strength from test_case_executions where id=?",
                        String.class,
                        executionId))
                .isEqualTo("NONE");
        UUID failureId = failure(
                runId, "1".repeat(64), "Connection refused by database", "java.net.ConnectException");

        assertThat(correlation.correlate(runId).strongMatches()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select correlated_failure_event_id from test_case_executions where id=?",
                        UUID.class,
                        executionId))
                .isEqualTo(failureId);
    }

    @Test
    void reportIngestionAutomaticallyAttemptsCorrelation() {
        UUID runId = pipelineRun(7003, 1, "automatic-sha");
        UUID failureId = failure(
                runId, "2".repeat(64), "expected <NUMBER>", "AssertionError");
        byte[] xml = """
                <testsuite name="CheckoutTest">
                  <testcase classname="CheckoutTest" name="rejectsInvalidTotal">
                    <failure type="AssertionError" message="expected 12345"/>
                  </testcase>
                </testsuite>
                """.getBytes();

        ingestion.ingest(runId, "automatic.xml", xml, "junit");

        var row = jdbc.queryForMap(
                """
                select correlated_failure_event_id, correlation_strength, failure_fingerprint
                from test_case_executions where pipeline_run_id=?
                """,
                runId);
        assertThat(row.get("correlated_failure_event_id")).isEqualTo(failureId);
        assertThat(row.get("correlation_strength")).isEqualTo("STRONG");
        assertThat(row.get("failure_fingerprint")).isNotNull();
    }

    private UUID pipelineRun(long externalRunId, int attempt, String sha) {
        UUID repositoryId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "correlation-owner",
                "repo-" + repositoryId,
                now,
                now);
        jdbc.update(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,ingested_at)
                values(?,?,?,?,?,?,?,?)
                """,
                runId,
                repositoryId,
                externalRunId,
                sha,
                "COMPLETED",
                "FAILURE",
                attempt,
                now);
        return runId;
    }

    private UUID failure(UUID runId, String fingerprint, String message, String exceptionType) {
        UUID failureId = UUID.randomUUID();
        jdbc.update(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,exception_type,normalized_message,
                    fingerprint,fingerprint_algorithm,occurrence_count)
                values(?,?,?,?,?,?,?,?)
                """,
                failureId,
                runId,
                "EXCEPTION",
                exceptionType,
                message,
                fingerprint,
                "v1",
                1);
        return failureId;
    }

    private UUID execution(
            UUID runId,
            String stableId,
            String status,
            String fingerprint,
            String message,
            String failureType) {
        UUID reportId = UUID.randomUUID();
        UUID suiteId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        jdbc.update(
                """
                insert into test_reports(
                    id,pipeline_run_id,source_name,content_sha256,total_tests,passed,failed,errors,skipped)
                values(?,?,?,?,?,?,?,?,?)
                """,
                reportId,
                runId,
                executionId + ".xml",
                "9".repeat(64),
                1,
                0,
                status.equals("FAILED") ? 1 : 0,
                status.equals("ERROR") ? 1 : 0,
                0);
        jdbc.update(
                "insert into test_suites(id,test_report_id,name) values(?,?,?)",
                suiteId,
                reportId,
                "suite");
        jdbc.update(
                """
                insert into test_case_executions(
                    id,test_suite_id,pipeline_run_id,stable_test_id,test_id_algorithm,test_name,
                    status,failure_type,normalized_failure_message,failure_fingerprint)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                executionId,
                suiteId,
                runId,
                stableId,
                "test-id-v1",
                "test",
                status,
                failureType,
                message,
                fingerprint);
        return executionId;
    }
}
