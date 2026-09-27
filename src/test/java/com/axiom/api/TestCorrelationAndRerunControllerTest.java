package com.axiom.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TestCorrelationAndRerunControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void correlationEndpointReturnsCountsAndMissingRunIsNotFound() throws Exception {
        UUID repositoryId = repository();
        UUID runId = pipelineRun(repositoryId, 8100, 1, "correlation-sha");
        String fingerprint = "c".repeat(64);
        failure(runId, fingerprint);
        execution(runId, "d".repeat(64), "FAILED", fingerprint);

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/tests/correlate", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineRunId").value(runId.toString()))
                .andExpect(jsonPath("$.failedExecutions").value(1))
                .andExpect(jsonPath("$.exactMatches").value(1))
                .andExpect(jsonPath("$.strongMatches").value(0))
                .andExpect(jsonPath("$.uncorrelated").value(0));

        mockMvc.perform(post(
                        "/api/v1/pipeline-runs/{id}/tests/correlate", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rerunEndpointReturnsExplicitTransitionAndUnknownTestIsNotFound() throws Exception {
        UUID repositoryId = repository();
        UUID attemptTwo = pipelineRun(repositoryId, 8200, 2, "same-sha");
        UUID attemptOne = pipelineRun(repositoryId, 8200, 1, "same-sha");
        String stableId = "e".repeat(64);
        execution(attemptTwo, stableId, "PASSED", null);
        execution(attemptOne, stableId, "FAILED", "f".repeat(64));

        mockMvc.perform(get("/api/v1/tests/{stableTestId}/reruns", stableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalRunId").value(8200))
                .andExpect(jsonPath("$[0].fromAttempt").value(1))
                .andExpect(jsonPath("$[0].toAttempt").value(2))
                .andExpect(jsonPath("$[0].sameCommitSha").value(true))
                .andExpect(jsonPath("$[0].transitionType").value("FAIL_TO_PASS"));

        mockMvc.perform(get("/api/v1/tests/{stableTestId}/history", stableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/tests/{stableTestId}/reruns", "unknown-test"))
                .andExpect(status().isNotFound());
    }

    private UUID repository() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                id,
                "GITHUB_ACTIONS",
                "api-owner",
                "repo-" + id,
                now,
                now);
        return id;
    }

    private UUID pipelineRun(UUID repositoryId, long externalId, int attempt, String sha) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,ingested_at)
                values(?,?,?,?,?,?,?,?)
                """,
                id,
                repositoryId,
                externalId,
                sha,
                "COMPLETED",
                "FAILURE",
                attempt,
                Timestamp.from(Instant.now()));
        return id;
    }

    private void failure(UUID runId, String fingerprint) {
        jdbc.update(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,exception_type,normalized_message,
                    fingerprint,fingerprint_algorithm,occurrence_count)
                values(?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                runId,
                "ASSERTION",
                "AssertionError",
                "expected value",
                fingerprint,
                "v1",
                1);
    }

    private void execution(UUID runId, String stableId, String status, String fingerprint) {
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
                executionId.toString().replace("-", "") + "0".repeat(32),
                1,
                status.equals("PASSED") ? 1 : 0,
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
                status.equals("FAILED") ? "AssertionError" : null,
                status.equals("FAILED") ? "expected value" : null,
                fingerprint);
    }
}
