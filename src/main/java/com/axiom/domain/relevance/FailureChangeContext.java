package com.axiom.domain.relevance;

import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.test.CorrelationStrength;
import com.axiom.domain.test.RerunTransition;
import java.util.List;
import java.util.UUID;

public record FailureChangeContext(
        UUID pipelineRunId,
        UUID repositoryId,
        UUID failureEventId,
        String fingerprint,
        String normalizedMessage,
        String exceptionType,
        FailureClassification classification,
        String baseSha,
        String headSha,
        List<ContextChangedFile> changedFiles,
        CorrelatedTest correlatedTest,
        TestHistory testHistory,
        FingerprintHistory fingerprintHistory,
        List<RerunTransition> rerunTransitions) {
    public FailureChangeContext {
        changedFiles = List.copyOf(changedFiles);
        rerunTransitions = List.copyOf(rerunTransitions);
    }

    public record ContextChangedFile(UUID id, ChangedFile file) {}

    public record CorrelatedTest(
            UUID executionId,
            String stableTestId,
            String className,
            String testName,
            String suiteName,
            CorrelationStrength correlationStrength) {}

    public record TestHistory(
            int executions, int passed, int failed, int errors, int skipped) {}

    public record FingerprintHistory(int priorOccurrences, List<UUID> priorPipelineRunIds) {
        public FingerprintHistory {
            priorPipelineRunIds = List.copyOf(priorPipelineRunIds);
        }

        public boolean predatesCurrentRun() {
            return priorOccurrences > 0;
        }
    }
}
