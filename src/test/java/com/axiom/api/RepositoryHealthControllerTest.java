package com.axiom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.health.RepositoryHealthService;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.triage.RerunRecommendation;
import com.axiom.integrations.github.client.GitHubApiClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RepositoryHealthControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RepositoryHealthService health;
    @MockitoBean private GitHubApiClient gitHub;

    @Test
    void aggregatesBoundedPersistedRepositoryHealth() throws Exception {
        UUID repositoryId = repository();
        Instant now = Instant.now();
        UUID success = run(repositoryId, "SUCCESS", now.minus(1, ChronoUnit.DAYS));
        UUID failureA = run(repositoryId, "FAILURE", now.minus(2, ChronoUnit.DAYS));
        UUID cancelled = run(repositoryId, "CANCELLED", now.minus(3, ChronoUnit.DAYS));
        UUID failureB = run(repositoryId, "TIMED_OUT", now.minus(4, ChronoUnit.DAYS));
        UUID outsideWindow = run(repositoryId, "FAILURE", now.minus(60, ChronoUnit.DAYS));

        String frequent = "a".repeat(64);
        String secondary = "b".repeat(64);
        UUID frequentA = failure(failureA, frequent, 5);
        diagnosis(failureA, frequentA, frequent, "INFRASTRUCTURE_FAILURE");
        UUID frequentB = failure(failureB, frequent, 2);
        diagnosis(failureB, frequentB, frequent, "INFRASTRUCTURE_FAILURE");
        UUID secondaryFailure = failure(failureB, secondary, 3);
        diagnosis(failureB, secondaryFailure, secondary, "TEST_FAILURE");
        UUID oldFailure = failure(outsideWindow, "c".repeat(64), 99);
        diagnosis(outsideWindow, oldFailure, "c".repeat(64), "DEPENDENCY_FAILURE");

        triage(failureA, "RECOMMENDED");
        triage(failureB, "NOT_RECOMMENDED");
        triage(cancelled, "INSUFFICIENT_EVIDENCE");
        relevance(failureA, frequentA, frequent, "UNRELATED");
        relevance(failureB, frequentB, frequent, "UNLIKELY_RELATED");
        relevance(failureB, secondaryFailure, secondary, "RELATED");

        stability(failureA, TestStabilityClass.SUSPECTED_FLAKY);
        stability(failureA, TestStabilityClass.FLAKY);
        stability(failureB, TestStabilityClass.CONSISTENTLY_FAILING);

        var result = health.get(repositoryId, 30, 200);

        assertThat(result.window().analyzedRuns()).isEqualTo(4);
        assertThat(result.runs().total()).isEqualTo(4);
        assertThat(result.runs().successful()).isEqualTo(1);
        assertThat(result.runs().failed()).isEqualTo(2);
        assertThat(result.runs().cancelled()).isEqualTo(1);
        assertThat(result.runs().successRate()).isEqualTo(1.0 / 3.0);
        assertThat(result.failureClassifications())
                .containsEntry(FailureClassification.INFRASTRUCTURE_FAILURE, 2L)
                .containsEntry(FailureClassification.TEST_FAILURE, 1L)
                .containsEntry(FailureClassification.DEPENDENCY_FAILURE, 0L);
        assertThat(result.rerunRecommendations())
                .containsEntry(RerunRecommendation.RECOMMENDED, 1L)
                .containsEntry(RerunRecommendation.NOT_RECOMMENDED, 1L)
                .containsEntry(RerunRecommendation.INSUFFICIENT_EVIDENCE, 1L)
                .containsEntry(RerunRecommendation.CONSIDER, 0L);
        assertThat(result.changeRelevance())
                .containsEntry(ChangeRelevance.RELATED, 1L)
                .containsEntry(ChangeRelevance.UNLIKELY_RELATED, 1L)
                .containsEntry(ChangeRelevance.UNRELATED, 1L)
                .containsEntry(ChangeRelevance.INDETERMINATE, 0L);
        assertThat(result.testStability().suspectedFlaky()).isEqualTo(1);
        assertThat(result.testStability().flaky()).isEqualTo(1);
        assertThat(result.testStability().consistentlyFailing()).isEqualTo(1);
        assertThat(result.topFailureFingerprints())
                .extracting("fingerprint", "occurrenceCount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(frequent, 7L),
                        org.assertj.core.groups.Tuple.tuple(secondary, 3L));

        mockMvc.perform(get("/api/v1/repositories/{id}/health", repositoryId)
                        .queryParam("days", "30")
                        .queryParam("maxRuns", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.window.analyzedRuns").value(4))
                .andExpect(jsonPath("$.runs.successRate").value(1.0 / 3.0))
                .andExpect(jsonPath("$.failureClassifications.INFRASTRUCTURE_FAILURE")
                        .value(2))
                .andExpect(jsonPath("$.rerunRecommendations.RECOMMENDED").value(1))
                .andExpect(jsonPath("$.changeRelevance.RELATED").value(1))
                .andExpect(jsonPath("$.testStability.suspectedFlaky").value(1))
                .andExpect(jsonPath("$.topFailureFingerprints[0].fingerprint")
                        .value(frequent));
        verifyNoInteractions(gitHub);
        assertThat(success).isNotNull();
    }

    @Test
    void honorsRunAndDayBoundsAndReturnsZeroState() throws Exception {
        UUID repositoryId = repository();
        Instant now = Instant.now();
        run(repositoryId, "FAILURE", now.minus(2, ChronoUnit.DAYS));
        run(repositoryId, "SUCCESS", now.minus(1, ChronoUnit.DAYS));

        var limited = health.get(repositoryId, 30, 1);
        assertThat(limited.runs().total()).isEqualTo(1);
        assertThat(limited.runs().successful()).isEqualTo(1);

        UUID emptyRepository = repository();
        mockMvc.perform(get("/api/v1/repositories/{id}/health", emptyRepository))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs.total").value(0))
                .andExpect(jsonPath("$.runs.successRate").value(0.0))
                .andExpect(jsonPath("$.topFailureFingerprints.length()").value(0));
        verifyNoInteractions(gitHub);
    }

    @Test
    void validatesBoundsAndMissingRepository() throws Exception {
        UUID repositoryId = repository();
        mockMvc.perform(get("/api/v1/repositories/{id}/health", repositoryId)
                        .queryParam("days", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/repositories/{id}/health", repositoryId)
                        .queryParam("maxRuns", "1001"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/repositories/{id}/health", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void remainsBoundedWithHundredsOfRunsAndManyFailureEvents() {
        UUID repositoryId = repository();
        Instant now = Instant.now();
        var runRows = IntStream.range(0, 250)
                .mapToObj(index -> {
                    UUID id = UUID.randomUUID();
                    Instant occurred = now.minus(index, ChronoUnit.MINUTES);
                    return new Object[] {
                        id,
                        repositoryId,
                        1_000_000L + index,
                        id.toString(),
                        "COMPLETED",
                        index % 2 == 0 ? "SUCCESS" : "FAILURE",
                        1,
                        Timestamp.from(occurred),
                        Timestamp.from(occurred),
                        Timestamp.from(occurred)
                    };
                })
                .toList();
        jdbc.batchUpdate(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,
                    started_at,finished_at,ingested_at)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                runRows);
        UUID failedRun = (UUID) runRows.get(1)[0];
        var failures = IntStream.range(0, 100)
                .mapToObj(index -> new Object[] {
                    UUID.randomUUID(),
                    failedRun,
                    "EXCEPTION",
                    "bounded fixture " + index,
                    "%064x".formatted(index),
                    "v1",
                    1,
                    index + 1,
                    index + 1
                })
                .toList();
        jdbc.batchUpdate(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,normalized_message,fingerprint,
                    fingerprint_algorithm,occurrence_count,first_line,last_line)
                values(?,?,?,?,?,?,?,?,?)
                """,
                failures);

        var result = health.get(repositoryId, 30, 200);

        assertThat(result.runs().total()).isEqualTo(200);
        assertThat(result.topFailureFingerprints()).hasSize(10);
    }

    private UUID repository() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                id,
                "GITHUB_ACTIONS",
                "health-owner",
                "repo-" + id,
                now,
                now);
        return id;
    }

    private UUID run(UUID repositoryId, String conclusion, Instant occurredAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,
                    started_at,finished_at,ingested_at)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                repositoryId,
                Math.abs(id.getMostSignificantBits()),
                id.toString(),
                "COMPLETED",
                conclusion,
                1,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt));
        return id;
    }

    private UUID failure(UUID runId, String fingerprint, int occurrences) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,normalized_message,fingerprint,
                    fingerprint_algorithm,occurrence_count,first_line,last_line)
                values(?,?,?,?,?,?,?,?,?)
                """,
                id,
                runId,
                "EXCEPTION",
                "fixture failure",
                fingerprint,
                "v1",
                occurrences,
                1,
                1);
        return id;
    }

    private void diagnosis(
            UUID runId, UUID failureId, String fingerprint, String classification) {
        jdbc.update(
                """
                insert into failure_diagnoses(
                    id,pipeline_run_id,failure_event_id,fingerprint,classification,confidence,
                    confidence_level,summary,classifier_version)
                values(?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                runId,
                failureId,
                fingerprint,
                classification,
                .9,
                "HIGH",
                "fixture diagnosis",
                "axiom-classifier-v1");
    }

    private void triage(UUID runId, String recommendation) {
        jdbc.update(
                """
                insert into pipeline_triage_results(
                    id,pipeline_run_id,rerun_recommendation,rerun_confidence,summary,
                    triage_version,status,created_at)
                values(?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                runId,
                recommendation,
                .8,
                "fixture triage",
                "triage-v1",
                "COMPLETED",
                Timestamp.from(Instant.now()));
    }

    private void relevance(
            UUID runId, UUID failureId, String fingerprint, String relevance) {
        jdbc.update(
                """
                insert into change_relevance_results(
                    id,pipeline_run_id,failure_event_id,fingerprint,relevance,confidence,
                    analyzer_version,summary)
                values(?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                runId,
                failureId,
                fingerprint,
                relevance,
                .8,
                "change-relevance-v1",
                "fixture relevance");
    }

    private void stability(UUID runId, TestStabilityClass classification) {
        String stableId = UUID.randomUUID().toString().replace("-", "").repeat(2);
        UUID reportId = UUID.randomUUID();
        UUID suiteId = UUID.randomUUID();
        jdbc.update(
                """
                insert into test_reports(
                    id,pipeline_run_id,source_name,content_sha256,total_tests,passed,
                    failed,errors,skipped)
                values(?,?,?,?,?,?,?,?,?)
                """,
                reportId,
                runId,
                stableId + ".xml",
                stableId,
                1,
                1,
                0,
                0,
                0);
        jdbc.update(
                "insert into test_suites(id,test_report_id,name) values(?,?,?)",
                suiteId,
                reportId,
                "suite");
        jdbc.update(
                """
                insert into test_case_executions(
                    id,test_suite_id,pipeline_run_id,stable_test_id,test_id_algorithm,
                    test_name,status)
                values(?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                suiteId,
                runId,
                stableId,
                "test-id-v1",
                "test",
                "PASSED");
        jdbc.update(
                """
                insert into test_stability_snapshots(
                    id,stable_test_id,stability_class,classifier_version,total_executions,
                    pass_count,failure_count,error_count,fail_to_pass_reruns,
                    unique_failure_fingerprints)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                stableId,
                classification.name(),
                "stability-v1",
                5,
                3,
                2,
                0,
                1,
                1);
    }
}
