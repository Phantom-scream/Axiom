package com.axiom.domain.analysis;

import java.util.List;
import java.util.UUID;

public record PipelineAnalysisResult(
        UUID pipelineRunId,
        boolean recompute,
        List<AnalysisStageResult> stages,
        boolean triageAvailable) {
    public PipelineAnalysisResult {
        stages = List.copyOf(stages);
    }
}
