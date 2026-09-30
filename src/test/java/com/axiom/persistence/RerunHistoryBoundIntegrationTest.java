package com.axiom.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.axiom.IntegrationTestSupport;
import com.axiom.application.testreport.RerunAnalysisService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RerunHistoryBoundIntegrationTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RerunAnalysisService reruns;

    @Test
    void thousandExecutionBoundUsesChronologyAndRetainsAttemptOrdering() {
        UUID repository = UUID.randomUUID();
        UUID report = UUID.randomUUID();
        UUID suite = UUID.randomUUID();
        String stableId = "b".repeat(64);
        jdbc.update("insert into repositories(id,provider,owner,name) values(?,'GITHUB_ACTIONS','bounded-history','fixture')", repository);
        jdbc.update("""
                insert into pipeline_runs(id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,finished_at)
                select gen_random_uuid(),?,919191,'same-sha','COMPLETED','FAILURE',n,
                       timestamp '2026-09-01 00:00:00' + n * interval '1 minute'
                from generate_series(1,1005) n
                """, repository);
        UUID first = jdbc.queryForObject("select id from pipeline_runs where repository_id=? and attempt=1", UUID.class, repository);
        jdbc.update("insert into test_reports(id,pipeline_run_id,source_name,content_sha256,total_tests,passed,failed,errors,skipped) values(?,?,'fixture.xml',?,1005,0,1005,0,0)", report, first, "b".repeat(64));
        jdbc.update("insert into test_suites(id,test_report_id,name) values(?,?,'fixture')", suite, report);
        jdbc.update("""
                insert into test_case_executions(id,test_suite_id,pipeline_run_id,stable_test_id,test_id_algorithm,test_name,status,failure_fingerprint)
                select gen_random_uuid(),?,id,?,'test-id-v1','bounded','FAILED','same-fingerprint'
                from pipeline_runs where repository_id=? order by attempt desc
                """, suite, stableId, repository);
        var transitions = reruns.transitions(stableId);
        assertThat(transitions).hasSize(999);
        assertThat(transitions.getFirst().fromAttempt()).isEqualTo(6);
        assertThat(transitions.getLast().toAttempt()).isEqualTo(1005);
        assertThat(transitions).allMatch(transition -> transition.sameCommitSha());
    }
}
