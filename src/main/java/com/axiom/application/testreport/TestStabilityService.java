package com.axiom.application.testreport;

import com.axiom.domain.test.RerunTransitionType;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.test.TestStatus;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TestStabilityService {
    private final RerunAnalysisService rerunAnalysis;

    public TestStabilityService(RerunAnalysisService rerunAnalysis) {
        this.rerunAnalysis = rerunAnalysis;
    }

    public Result analyze(String stableTestId, List<TestStatus> statuses, int sameFingerprintFailures) {
        int failToPass = (int) rerunAnalysis.transitions(stableTestId).stream()
                .filter(transition -> transition.sameCommitSha()
                        && (transition.transitionType() == RerunTransitionType.FAIL_TO_PASS
                                || transition.transitionType() == RerunTransitionType.ERROR_TO_PASS))
                .count();
        return classify(statuses, failToPass, sameFingerprintFailures);
    }

    public Result classify(List<TestStatus> statuses, int failToPass, int sameFingerprintFailures) {
        int total = statuses.size();
        int fails = (int) statuses.stream()
                .filter(status -> status == TestStatus.FAILED || status == TestStatus.ERROR)
                .count();
        int passes = (int) statuses.stream().filter(status -> status == TestStatus.PASSED).count();
        TestStabilityClass classification;
        if (total < 5) classification = TestStabilityClass.INSUFFICIENT_HISTORY;
        else if (failToPass >= 2) classification = TestStabilityClass.FLAKY;
        else if (failToPass == 1) classification = TestStabilityClass.SUSPECTED_FLAKY;
        else if (fails >= 4 && fails / (double) total >= .8 && sameFingerprintFailures >= 4) {
            classification = TestStabilityClass.CONSISTENTLY_FAILING;
        } else if (total >= 20 && fails == 0) classification = TestStabilityClass.STABLE;
        else if (fails == 0) classification = TestStabilityClass.LIKELY_STABLE;
        else classification = TestStabilityClass.INSUFFICIENT_HISTORY;
        String evidence = switch (classification) {
            case FLAKY -> "MULTIPLE_FAIL_TO_PASS_TRANSITIONS";
            case SUSPECTED_FLAKY -> "UNCHANGED_RERUN_FAIL_TO_PASS";
            case CONSISTENTLY_FAILING -> "REPEATED_SAME_FAILURE";
            default -> "EXECUTION_HISTORY";
        };
        return new Result(
                classification,
                "stability-v1",
                total,
                passes,
                fails,
                failToPass,
                List.of(evidence));
    }

    public record Result(
            TestStabilityClass classification,
            String version,
            int total,
            int passed,
            int failed,
            int failToPass,
            List<String> evidence) {}
}
