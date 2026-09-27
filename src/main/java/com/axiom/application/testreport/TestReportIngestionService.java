package com.axiom.application.testreport;

import com.axiom.analysis.fingerprint.Sha256FailureFingerprinter;
import com.axiom.analysis.logs.LogNormalizer;
import com.axiom.domain.failure.FailureEventType;
import com.axiom.domain.test.TestCaseExecution;
import com.axiom.domain.test.TestReport;
import com.axiom.domain.test.TestStatus;
import com.axiom.integrations.testreport.TestReportParser;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestReportIngestionService {
    private final TestReportParser parser;
    private final JdbcTemplate jdbc;
    private final LogNormalizer normalizer;
    private final Sha256FailureFingerprinter fingerprinter;
    private final TestFailureCorrelationService correlation;

    public TestReportIngestionService(
            TestReportParser parser,
            JdbcTemplate jdbc,
            LogNormalizer normalizer,
            Sha256FailureFingerprinter fingerprinter,
            TestFailureCorrelationService correlation) {
        this.parser = parser;
        this.jdbc = jdbc;
        this.normalizer = normalizer;
        this.fingerprinter = fingerprinter;
        this.correlation = correlation;
    }

    @Transactional
    public Map<String, Object> ingest(UUID run, String source, byte[] content, String hint) {
        TestReport report = parser.parse(source, content, hint);
        String hash = hash(content);
        UUID existing = jdbc.query(
                """
                select id from test_reports
                where pipeline_run_id=? and source_name=? and content_sha256=?
                """,
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                run,
                source,
                hash);
        if (existing != null) {
            correlation.correlate(run);
            return Map.of(
                    "testReportId", existing,
                    "pipelineRunId", run,
                    "totalTests", report.total(),
                    "idempotent", true);
        }

        UUID reportId = UUID.randomUUID();
        int passed = 0;
        int failed = 0;
        int errors = 0;
        int skipped = 0;
        for (var suite : report.suites()) {
            for (var test : suite.testCases()) {
                switch (test.status()) {
                    case PASSED -> passed++;
                    case FAILED -> failed++;
                    case ERROR -> errors++;
                    case SKIPPED, DISABLED -> skipped++;
                    default -> {}
                }
            }
        }
        jdbc.update(
                """
                insert into test_reports(
                    id,pipeline_run_id,source_name,framework_hint,content_sha256,
                    total_tests,passed,failed,errors,skipped)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                reportId,
                run,
                source,
                hint,
                hash,
                report.total(),
                passed,
                failed,
                errors,
                skipped);
        for (var suite : report.suites()) {
            UUID suiteId = UUID.randomUUID();
            jdbc.update(
                    "insert into test_suites(id,test_report_id,name) values(?,?,?)",
                    suiteId,
                    reportId,
                    suite.name());
            for (var test : suite.testCases()) {
                String normalizedMessage = normalizedMessage(test);
                jdbc.update(
                        """
                        insert into test_case_executions(
                            id,test_suite_id,pipeline_run_id,stable_test_id,test_id_algorithm,
                            class_name,test_name,status,duration_ms,failure_type,failure_message,
                            normalized_failure_message,failure_fingerprint)
                        values(?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                        UUID.randomUUID(),
                        suiteId,
                        run,
                        test.stableTestId(),
                        "test-id-v1",
                        test.className(),
                        test.testName(),
                        test.status().name(),
                        test.durationMs(),
                        test.failureType(),
                        test.failureMessage(),
                        normalizedMessage,
                        failureFingerprint(test, normalizedMessage));
            }
        }
        correlation.correlate(run);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("testReportId", reportId);
        response.put("pipelineRunId", run);
        response.put("totalTests", report.total());
        response.put("passed", passed);
        response.put("failed", failed);
        response.put("errors", errors);
        response.put("skipped", skipped);
        response.put("suiteCount", report.suites().size());
        return Map.copyOf(response);
    }

    private String normalizedMessage(TestCaseExecution test) {
        return test.failureMessage() == null ? null : normalizer.sanitize(test.failureMessage());
    }

    private String failureFingerprint(TestCaseExecution test, String normalizedMessage) {
        if (test.status() != TestStatus.FAILED && test.status() != TestStatus.ERROR) return null;
        FailureEventType type = test.status() == TestStatus.FAILED
                || containsIgnoreCase(test.failureType(), "assert")
                ? FailureEventType.ASSERTION
                : FailureEventType.EXCEPTION;
        return fingerprinter.fingerprint(
                type, test.failureType(), normalizedMessage == null ? "" : normalizedMessage, stackRoot(test));
    }

    private String stackRoot(TestCaseExecution test) {
        if (test.stackTrace() == null) return "";
        return test.stackTrace().lines()
                .map(String::trim)
                .filter(line -> line.startsWith("at "))
                .findFirst()
                .map(normalizer::sanitize)
                .orElse("");
    }

    private boolean containsIgnoreCase(String value, String part) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(part);
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
