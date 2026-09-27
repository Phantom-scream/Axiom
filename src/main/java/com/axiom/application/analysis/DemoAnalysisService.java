package com.axiom.application.analysis;

import com.axiom.analysis.classification.FailureClassifier;
import com.axiom.domain.diagnosis.AnalysisResult;
import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.PipelineConclusion;
import com.axiom.domain.pipeline.PipelineRun;
import com.axiom.domain.pipeline.PipelineStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DemoAnalysisService {
    private final FailureClassifier classifier;
    private final Clock clock;
    public DemoAnalysisService(FailureClassifier classifier, Clock clock) { this.classifier = classifier; this.clock = clock; }
    public AnalysisResult analyze(String log) {
        Instant now = clock.instant();
        PipelineRun run = new PipelineRun(UUID.nameUUIDFromBytes("axiom-demo-run".getBytes()), CiProviderType.GITHUB_ACTIONS, 1L, "axiom", "demo", "0000000", null, null, "main", null, PipelineStatus.COMPLETED, PipelineConclusion.FAILURE, 1, now, now, List.of());
        return new AnalysisResult(run, classifier.classify(new AnalysisContext(run, log)), now);
    }
}
