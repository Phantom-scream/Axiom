package com.axiom.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.analysis.FailureChangeContextService;
import com.axiom.application.analysis.PipelineChangeAnalysisService;
import com.axiom.domain.relevance.ChangeRelevance;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ChangeRelevancePersistenceIntegrationTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FailureChangeContextService contexts;
    @Autowired private PipelineChangeAnalysisService analysis;

    @Test
    void buildsPersistedContextAndIdempotentlyReplacesEvidence() {
        UUID repositoryId = repository();
        Instant oldTime = Instant.parse("2026-01-01T00:00:00Z");
        Instant currentTime = Instant.parse("2026-01-02T00:00:00Z");
        UUID priorRun = run(repositoryId, 8800, 1, "old-sha", oldTime);
        UUID currentRun = run(repositoryId, 8801, 1, "same-sha", currentTime);
        UUID rerun = run(repositoryId, 8801, 2, "same-sha", currentTime.plusSeconds(60));
        String fingerprint = "a".repeat(64);
        failure(priorRun, fingerprint, "assertion failed");
        UUID currentFailure = failure(currentRun, fingerprint, "assertion failed");
        diagnosis(currentRun, currentFailure, fingerprint, "TEST_FAILURE");
        UUID changedFileId = changes(
                currentRun,
                "src/main/java/com/example/PaymentService.java",
                "PRODUCTION_SOURCE");
        String stableTestId = "b".repeat(64);
        testExecution(
                currentRun,
                stableTestId,
                "FAILED",
                fingerprint,
                currentFailure,
                "EXACT",
                "com.example.PaymentServiceTest");
        testExecution(
                rerun,
                stableTestId,
                "PASSED",
                null,
                null,
                null,
                "com.example.PaymentServiceTest");

        var context = contexts.build(currentRun, currentFailure);
        assertThat(context.classification().name()).isEqualTo("TEST_FAILURE");
        assertThat(context.correlatedTest()).isNotNull();
        assertThat(context.testHistory()).isNotNull();
        assertThat(context.testHistory().executions()).isEqualTo(2);
        assertThat(context.fingerprintHistory().priorOccurrences()).isEqualTo(1);
        assertThat(context.rerunTransitions()).singleElement().satisfies(transition -> {
            assertThat(transition.sameCommitSha()).isTrue();
            assertThat(transition.transitionType().name()).isEqualTo("FAIL_TO_PASS");
        });

        analysis.analyze(currentRun);
        analysis.analyze(currentRun);

        assertThat(jdbc.queryForObject(
                        "select count(*) from change_relevance_results where pipeline_run_id=?",
                        Integer.class,
                        currentRun))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from change_relevance_evidence e
                        join change_relevance_results r on r.id=e.relevance_result_id
                        where r.pipeline_run_id=?
                        """,
                        Integer.class,
                        currentRun))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from relevance_related_files rf
                        join change_relevance_results r on r.id=rf.relevance_result_id
                        where r.pipeline_run_id=? and rf.changed_file_id=?
                        """,
                        Integer.class,
                        currentRun,
                        changedFileId))
                .isEqualTo(1);

        var persisted = analysis.get(currentRun).getFirst();
        assertThat(persisted.analyzerVersion()).isEqualTo("change-relevance-v1");
        assertThat(persisted.relevance())
                .isIn(ChangeRelevance.UNLIKELY_RELATED, ChangeRelevance.UNRELATED);
        assertThat(persisted.evidence())
                .extracting("code")
                .containsExactlyInAnyOrder(
                        "SAME_FINGERPRINT_PREDATES_CHANGE",
                        "UNCHANGED_RERUN_FAIL_TO_PASS",
                        "RELATED_PRODUCTION_FILE_CHANGED");
        assertThat(persisted.evidence())
                .filteredOn(evidence -> evidence.code().equals("SAME_FINGERPRINT_PREDATES_CHANGE"))
                .singleElement()
                .satisfies(evidence -> assertThat(evidence.metadata()).containsEntry("priorOccurrences", "1"));
    }

    @Test
    void dependencyEvidencePersistsAsRelated() {
        UUID repositoryId = repository();
        UUID runId = run(repositoryId, 8900, 1, "dependency-sha", Instant.parse("2026-02-01T00:00:00Z"));
        String fingerprint = "c".repeat(64);
        UUID failureId = failure(runId, fingerprint, "Could not resolve artifact");
        diagnosis(runId, failureId, fingerprint, "DEPENDENCY_FAILURE");
        changes(runId, "pom.xml", "DEPENDENCY_MANIFEST");

        var summary = analysis.analyze(runId);
        var persisted = analysis.get(runId).getFirst();

        assertThat(summary.results().get(ChangeRelevance.RELATED)).isEqualTo(1);
        assertThat(persisted.relevance()).isEqualTo(ChangeRelevance.RELATED);
        assertThat(persisted.evidence()).extracting("code").contains("DEPENDENCY_FILE_CHANGED");
    }

    @Test
    void contextAllowsMissingDiagnosisAndUsesStrongCorrelationConservatively() {
        UUID repositoryId = repository();
        UUID runId = run(repositoryId, 8950, 1, "strong-sha", Instant.parse("2026-03-01T00:00:00Z"));
        String fingerprint = "d".repeat(64);
        UUID failureId = failure(runId, fingerprint, "assertion failed");
        changes(
                runId,
                "src/test/java/com/example/PaymentServiceTest.java",
                "TEST_SOURCE");
        testExecution(
                runId,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                "FAILED",
                fingerprint,
                failureId,
                "STRONG",
                "com.example.PaymentServiceTest");

        var context = contexts.build(runId, failureId);

        assertThat(context.classification().name()).isEqualTo("UNKNOWN");
        assertThat(context.correlatedTest().correlationStrength().name()).isEqualTo("STRONG");
        assertThat(context.fingerprintHistory().priorOccurrences()).isZero();
        assertThat(context.rerunTransitions()).isEmpty();
        assertThat(context.testHistory().executions()).isEqualTo(1);
    }

    @Test
    void analyzesEveryFailureAndPersistsDocumentationCounterEvidence() {
        UUID repositoryId = repository();
        UUID runId = run(repositoryId, 8990, 1, "docs-sha", Instant.parse("2026-04-01T00:00:00Z"));
        String infrastructureFingerprint = "1".repeat(64);
        UUID infrastructureFailure =
                failure(runId, infrastructureFingerprint, "database unavailable");
        diagnosis(
                runId,
                infrastructureFailure,
                infrastructureFingerprint,
                "INFRASTRUCTURE_FAILURE");
        failure(runId, "2".repeat(64), "unclassified secondary failure");
        changes(runId, "README.md", "DOCUMENTATION");

        var summary = analysis.analyze(runId);

        assertThat(summary.analyzedFailures()).isEqualTo(2);
        assertThat(analysis.get(runId)).hasSize(2);
        var infrastructure = analysis.get(runId, infrastructureFingerprint);
        assertThat(infrastructure.relevance())
                .isIn(ChangeRelevance.UNRELATED, ChangeRelevance.UNLIKELY_RELATED);
        assertThat(infrastructure.evidence())
                .extracting("code")
                .contains("ONLY_DOCUMENTATION_CHANGED");
    }

    private UUID repository() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                id,
                "GITHUB_ACTIONS",
                "relevance-owner",
                "repo-" + id,
                now,
                now);
        return id;
    }

    private UUID run(UUID repositoryId, long externalId, int attempt, String sha, Instant occurredAt) {
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
                "FAILURE",
                attempt,
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt));
        return id;
    }

    private UUID failure(UUID runId, String fingerprint, String message) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,exception_type,normalized_message,
                    fingerprint,fingerprint_algorithm,occurrence_count)
                values(?,?,?,?,?,?,?,?)
                """,
                id,
                runId,
                "EXCEPTION",
                "AssertionError",
                message,
                fingerprint,
                "v1",
                1);
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

    private UUID changes(UUID runId, String path, String category) {
        UUID setId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        jdbc.update(
                """
                insert into git_change_sets(
                    id,pipeline_run_id,base_sha,head_sha,total_changed_files,provider)
                values(?,?,?,?,?,?)
                """,
                setId,
                runId,
                "base",
                "head",
                1,
                "GITHUB");
        jdbc.update(
                """
                insert into changed_files(
                    id,change_set_id,path,change_type,file_category,additions,deletions,changes)
                values(?,?,?,?,?,?,?,?)
                """,
                fileId,
                setId,
                path,
                "MODIFIED",
                category,
                1,
                0,
                1);
        return fileId;
    }

    private void testExecution(
            UUID runId,
            String stableId,
            String status,
            String fingerprint,
            UUID correlatedFailure,
            String correlationStrength,
            String className) {
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
                "payments");
        jdbc.update(
                """
                insert into test_case_executions(
                    id,test_suite_id,pipeline_run_id,stable_test_id,test_id_algorithm,class_name,
                    test_name,status,failure_fingerprint,correlated_failure_event_id,correlation_strength)
                values(?,?,?,?,?,?,?,?,?,?,?)
                """,
                executionId,
                suiteId,
                runId,
                stableId,
                "test-id-v1",
                className,
                "rejectsInvalidPayment",
                status,
                fingerprint,
                correlatedFailure,
                correlationStrength);
    }
}
