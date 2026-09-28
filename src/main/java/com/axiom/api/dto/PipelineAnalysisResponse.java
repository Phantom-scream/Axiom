package com.axiom.api.dto;

import com.axiom.domain.analysis.AnalysisStage;
import com.axiom.domain.analysis.AnalysisStageStatus;
import com.axiom.domain.analysis.PipelineAnalysisResult;
import java.util.List;
import java.util.UUID;

public record PipelineAnalysisResponse(
        UUID pipelineRunId,
        boolean recompute,
        List<StageResponse> stages,
        boolean triageAvailable) {
    public static PipelineAnalysisResponse from(PipelineAnalysisResult result) {
        return new PipelineAnalysisResponse(
                result.pipelineRunId(),
                result.recompute(),
                result.stages().stream().map(StageResponse::from).toList(),
                result.triageAvailable());
    }

    public record StageResponse(
            AnalysisStage stage, AnalysisStageStatus status, String message, String errorCode) {
        static StageResponse from(com.axiom.domain.analysis.AnalysisStageResult result) {
            return new StageResponse(
                    result.stage(), result.status(), result.message(), result.errorCode());
        }
    }
}
