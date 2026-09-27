package com.axiom.analysis;

import com.axiom.analysis.classification.DeterministicFailureClassifier;
import com.axiom.application.analysis.AnalysisContext;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.pipeline.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeterministicFailureClassifierTest {
    @Test void classifiesDependencyResolutionEvidence() {
        var run = new PipelineRun(UUID.randomUUID(), CiProviderType.GITHUB_ACTIONS, 7L, "axiom", "demo", "abc", "main", null, PipelineStatus.COMPLETED, PipelineConclusion.FAILURE, 1, null, null, List.of());
        var result = new DeterministicFailureClassifier().classify(new AnalysisContext(run, "dependency could not be resolved"));
        assertThat(result.classification()).isEqualTo(FailureClassification.DEPENDENCY_FAILURE);
        assertThat(result.evidence()).extracting(e -> e.code()).containsExactly("DEPENDENCY_RESOLUTION_FAILED");
    }
}
