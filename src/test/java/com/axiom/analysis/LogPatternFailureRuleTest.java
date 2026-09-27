package com.axiom.analysis;

import com.axiom.analysis.classification.LogPatternFailureRule;
import com.axiom.application.analysis.AnalysisContext;
import com.axiom.domain.evidence.*;
import com.axiom.domain.pipeline.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LogPatternFailureRuleTest {
    @Test void emitsEvidenceWhenPatternMatches() {
        var rule = new LogPatternFailureRule("test", "needle", EvidenceType.LOG_PATTERN, EvidenceSeverity.HIGH, "MATCH", "Found it");
        var run = new PipelineRun(UUID.randomUUID(), CiProviderType.GITHUB_ACTIONS, 1L, "a", "b", "c", "main", null, PipelineStatus.COMPLETED, PipelineConclusion.FAILURE, 1, null, null, List.of());
        assertThat(rule.evaluate(new AnalysisContext(run, "a needle in a log"))).extracting(e -> e.code()).containsExactly("MATCH");
    }
}
