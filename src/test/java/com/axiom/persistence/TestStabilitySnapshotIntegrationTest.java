package com.axiom.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.testreport.TestStabilitySnapshotService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TestStabilitySnapshotIntegrationTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestStabilitySnapshotService snapshots;

    @Test
    void computesAndIdempotentlyUpdatesStabilityV1Snapshots() {
        UUID repositoryId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        UUID suiteId = UUID.randomUUID();
        String stableId = UUID.randomUUID().toString().replace("-", "").repeat(2);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "stability-owner",
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
                Math.abs(runId.getMostSignificantBits()),
                "same-sha",
                "COMPLETED",
                "FAILURE",
                1,
                now);
        jdbc.update(
                """
                insert into test_reports(
                    id,pipeline_run_id,source_name,content_sha256,total_tests,passed,
                    failed,errors,skipped)
                values(?,?,?,?,?,?,?,?,?)
                """,
                reportId,
                runId,
                "result.xml",
                "b".repeat(64),
                1,
                0,
                1,
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
                    test_name,status,failure_fingerprint)
                values(?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                suiteId,
                runId,
                stableId,
                "test-id-v1",
                "fails",
                "FAILED",
                "c".repeat(64));

        assertThat(snapshots.computeForPipeline(runId)).isEqualTo(1);
        assertThat(snapshots.computeForPipeline(runId)).isEqualTo(1);

        var stored = jdbc.queryForMap(
                "select stability_class,total_executions,failure_count,error_count from test_stability_snapshots where stable_test_id=? and classifier_version='stability-v1'",
                stableId);
        assertThat(stored.get("stability_class")).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(stored.get("total_executions")).isEqualTo(1);
        assertThat(stored.get("failure_count")).isEqualTo(1);
        assertThat(stored.get("error_count")).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                        "select count(*) from test_stability_snapshots where stable_test_id=?",
                        Integer.class,
                        stableId))
                .isEqualTo(1);
    }
}
