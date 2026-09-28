package com.axiom.application.testreport;

import com.axiom.domain.test.TestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestStabilitySnapshotService {
    private final JdbcTemplate jdbc;
    private final TestStabilityService stability;

    public TestStabilitySnapshotService(JdbcTemplate jdbc, TestStabilityService stability) {
        this.jdbc = jdbc;
        this.stability = stability;
    }

    @Transactional
    public int computeForPipeline(UUID pipelineRunId) {
        List<String> stableTestIds = jdbc.query(
                "select distinct trim(stable_test_id) from test_case_executions where pipeline_run_id=? order by 1",
                (rs, row) -> rs.getString(1),
                pipelineRunId);
        for (String stableTestId : stableTestIds) {
            List<Execution> executions = jdbc.query(
                    """
                    select status,failure_fingerprint from test_case_executions
                    where stable_test_id=? order by created_at,id
                    """,
                    (rs, row) -> new Execution(
                            TestStatus.valueOf(rs.getString("status")),
                            rs.getString("failure_fingerprint")),
                    stableTestId);
            List<TestStatus> statuses = executions.stream().map(Execution::status).toList();
            int sameFingerprintFailures = executions.stream()
                    .filter(value -> value.fingerprint() != null)
                    .collect(java.util.stream.Collectors.groupingBy(
                            Execution::fingerprint, java.util.stream.Collectors.counting()))
                    .values()
                    .stream()
                    .mapToInt(Long::intValue)
                    .max()
                    .orElse(0);
            var result = stability.analyze(stableTestId, statuses, sameFingerprintFailures);
            int errors = (int) statuses.stream().filter(status -> status == TestStatus.ERROR).count();
            int failures = (int) statuses.stream().filter(status -> status == TestStatus.FAILED).count();
            int uniqueFingerprints = (int) executions.stream()
                    .map(Execution::fingerprint)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            jdbc.update(
                    """
                    insert into test_stability_snapshots(
                        id,stable_test_id,stability_class,classifier_version,total_executions,
                        pass_count,failure_count,error_count,fail_to_pass_reruns,
                        unique_failure_fingerprints,computed_at)
                    values(?,?,?,?,?,?,?,?,?,?,current_timestamp)
                    on conflict(stable_test_id,classifier_version) do update set
                        stability_class=excluded.stability_class,
                        total_executions=excluded.total_executions,
                        pass_count=excluded.pass_count,
                        failure_count=excluded.failure_count,
                        error_count=excluded.error_count,
                        fail_to_pass_reruns=excluded.fail_to_pass_reruns,
                        unique_failure_fingerprints=excluded.unique_failure_fingerprints,
                        computed_at=excluded.computed_at
                    """,
                    UUID.randomUUID(),
                    stableTestId,
                    result.classification().name(),
                    result.version(),
                    result.total(),
                    result.passed(),
                    failures,
                    errors,
                    result.failToPass(),
                    uniqueFingerprints);
        }
        return stableTestIds.size();
    }

    private record Execution(TestStatus status, String fingerprint) {}
}
