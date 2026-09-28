package com.axiom.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.analysis.PipelineTriageApplicationService;
import com.axiom.domain.triage.ActionType;
import com.axiom.domain.triage.FailureRole;
import com.axiom.domain.triage.RerunRecommendation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PipelineTriagePersistenceIntegrationTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PipelineTriageApplicationService triage;

    @Test
    void persistsTransientInfrastructureTriageAndIdempotentlyReplacesChildren() {
        UUID repositoryId = repository();
        Instant oldTime = Instant.parse("2026-05-01T00:00:00Z");
        Instant currentTime = Instant.parse("2026-05-02T00:00:00Z");
        UUID historicalRun =
                run(repositoryId, 9200, 1, "old", "FAILURE", oldTime);
        UUID currentRun =
                run(repositoryId, 9201, 1, "same", "FAILURE", currentTime);
        UUID rerun = run(
                repositoryId,
                9201,
                2,
                "same",
                "SUCCESS",
                currentTime.plusSeconds(60));
        String infrastructureFingerprint = "3".repeat(64);
        failure(historicalRun, infrastructureFingerprint, "database unavailable", 1);
        UUID infrastructureFailure =
                failure(currentRun, infrastructureFingerprint, "database unavailable", 1);
        diagnosis(
                currentRun,
                infrastructureFailure,
                infrastructureFingerprint,
                "INFRASTRUCTURE_FAILURE");
        UUID genericFailure = failure(
                currentRun, "4".repeat(64), "process completed with exit code 1", 20);
        diagnosis(currentRun, genericFailure, "4".repeat(64), "BUILD_FAILURE");
        relevance(
                currentRun,
                infrastructureFailure,
                infrastructureFingerprint,
                "UNRELATED",
                "ONLY_DOCUMENTATION_CHANGED");
        String stableId = UUID.randomUUID().toString().replace("-", "").repeat(2);
        testExecution(
                currentRun,
                stableId,
                "FAILED",
                infrastructureFingerprint,
                infrastructureFailure,
                "EXACT");
        testExecution(rerun, stableId, "PASSED", null, null, null);
        stability(stableId, "SUSPECTED_FLAKY");

        var first = triage.compute(currentRun);
        int firstEvidenceCount = childCount("triage_evidence", currentRun);
        var second = triage.compute(currentRun);

        assertThat(first.primaryFailureEventId()).isEqualTo(infrastructureFailure);
        assertThat(first.failures())
                .filteredOn(entry -> entry.failureEventId().equals(genericFailure))
                .singleElement()
                .extracting("role")
                .isEqualTo(FailureRole.DOWNSTREAM);
        assertThat(second.rerunRecommendation()).isEqualTo(RerunRecommendation.RECOMMENDED);
        assertThat(second.actions().getFirst().type()).isEqualTo(ActionType.RERUN_PIPELINE);
        assertThat(second.actions()).extracting("type").contains(ActionType.INSPECT_INFRASTRUCTURE);
        assertThat(second.evidence())
                .extracting("code")
                .contains(
                        "UNCHANGED_RERUN_PASSED",
                        "FAILURE_PREDATES_CURRENT_RUN",
                        "CHANGE_RELEVANCE_ONLY_DOCUMENTATION_CHANGED");
        assertThat(jdbc.queryForObject(
                        "select count(*) from pipeline_triage_results where pipeline_run_id=?",
                        Integer.class,
                        currentRun))
                .isEqualTo(1);
        assertThat(childCount("failure_triage_entries", currentRun)).isEqualTo(2);
        assertThat(childCount("triage_actions", currentRun)).isEqualTo(2);
        assertThat(childCount("triage_evidence", currentRun)).isEqualTo(firstEvidenceCount);
    }

    @Test
    void relatedDependencyFailureDiscouragesRerunAndGeneratesDependencyAction() {
        UUID repositoryId = repository();
        UUID runId = run(
                repositoryId,
                9300,
                1,
                "dependency",
                "FAILURE",
                Instant.parse("2026-06-01T00:00:00Z"));
        String fingerprint = "5".repeat(64);
        UUID failureId = failure(runId, fingerprint, "Could not resolve artifact", 1);
        diagnosis(runId, failureId, fingerprint, "DEPENDENCY_FAILURE");
        relevance(runId, failureId, fingerprint, "RELATED", "DEPENDENCY_FILE_CHANGED");

        var result = triage.compute(runId);

        assertThat(result.rerunRecommendation()).isEqualTo(RerunRecommendation.NOT_RECOMMENDED);
        assertThat(result.actions())
                .extracting("type")
                .contains(ActionType.INSPECT_DEPENDENCY_CONFIGURATION)
                .doesNotContain(ActionType.RERUN_PIPELINE);
        assertThat(result.summary()).contains("dependency failure").contains("Investigation");
    }

    @Test
    void noFailuresAndSuccessfulRunsPersistControlledNonFailureStates() {
        UUID repositoryId = repository();
        UUID failedWithoutEvents = run(
                repositoryId,
                9400,
                1,
                "empty",
                "FAILURE",
                Instant.parse("2026-07-01T00:00:00Z"));
        UUID successful = run(
                repositoryId,
                9401,
                1,
                "success",
                "SUCCESS",
                Instant.parse("2026-07-02T00:00:00Z"));

        var insufficient = triage.compute(failedWithoutEvents);
        var noTriage = triage.compute(successful);

        assertThat(insufficient.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(insufficient.primaryFailureEventId()).isNull();
        assertThat(insufficient.rerunRecommendation())
                .isEqualTo(RerunRecommendation.INSUFFICIENT_EVIDENCE);
        assertThat(noTriage.status()).isEqualTo("NO_TRIAGE_NEEDED");
        assertThat(noTriage.actions()).isEmpty();
    }

    private int childCount(String table, UUID pipelineRunId) {
        return jdbc.queryForObject(
                "select count(*) from " + table
                        + " child join pipeline_triage_results t on t.id=child.triage_result_id where t.pipeline_run_id=?",
                Integer.class,
                pipelineRunId);
    }

    private UUID repository() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                id,
                "GITHUB_ACTIONS",
                "triage-owner",
                "repo-" + id,
                now,
                now);
        return id;
    }

    private UUID run(
            UUID repositoryId,
            long externalId,
            int attempt,
            String sha,
            String conclusion,
            Instant occurredAt) {
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
                externalId,
                sha,
                "COMPLETED",
                conclusion,
                attempt,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt));
        return id;
    }

    private UUID failure(UUID runId, String fingerprint, String message, int line) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,exception_type,normalized_message,
                    fingerprint,fingerprint_algorithm,occurrence_count,first_line,last_line)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                id,
                runId,
                "EXCEPTION",
                "RuntimeException",
                message,
                fingerprint,
                "v1",
                1,
                line,
                line);
        return id;
    }

    private void diagnosis(UUID runId, UUID failureId, String fingerprint, String classification) {
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

    private void relevance(
            UUID runId,
            UUID failureId,
            String fingerprint,
            String relevance,
            String evidenceCode) {
        UUID resultId = UUID.randomUUID();
        jdbc.update(
                """
                insert into change_relevance_results(
                    id,pipeline_run_id,failure_event_id,fingerprint,relevance,confidence,
                    analyzer_version,summary)
                values(?,?,?,?,?,?,?,?)
                """,
                resultId,
                runId,
                failureId,
                fingerprint,
                relevance,
                .9,
                "change-relevance-v1",
                "fixture relevance");
        jdbc.update(
                """
                insert into change_relevance_evidence(
                    id,relevance_result_id,code,priority,weight,description,metadata)
                values(?,?,?,?,?,?,cast(? as jsonb))
                """,
                UUID.randomUUID(),
                resultId,
                evidenceCode,
                relevance.equals("RELATED") ? "STRONG" : "COUNTER",
                relevance.equals("RELATED") ? .9 : -.8,
                "fixture evidence",
                "{}");
    }

    private void testExecution(
            UUID runId,
            String stableId,
            String status,
            String fingerprint,
            UUID correlatedFailure,
            String correlationStrength) {
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
                executionId.toString().replace("-", "").repeat(2),
                1,
                status.equals("PASSED") ? 1 : 0,
                status.equals("FAILED") ? 1 : 0,
                0,
                0);
        jdbc.update(
                "insert into test_suites(id,test_report_id,name) values(?,?,?)",
                suiteId,
                reportId,
                "integration");
        jdbc.update(
                """
                insert into test_case_executions(
                    id,test_suite_id,pipeline_run_id,stable_test_id,test_id_algorithm,
                    test_name,status,failure_fingerprint,correlated_failure_event_id,
                    correlation_strength)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                executionId,
                suiteId,
                runId,
                stableId,
                "test-id-v1",
                "databaseStarts",
                status,
                fingerprint,
                correlatedFailure,
                correlationStrength);
    }

    private void stability(String stableId, String classification) {
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
                classification,
                "stability-v1",
                5,
                4,
                1,
                0,
                1,
                1);
    }
}
