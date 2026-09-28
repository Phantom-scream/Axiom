package com.axiom.domain.analysis;

public record AnalysisStageResult(
        AnalysisStage stage, AnalysisStageStatus status, String message, String errorCode) {
    public static AnalysisStageResult completed(AnalysisStage stage, String message) {
        return new AnalysisStageResult(stage, AnalysisStageStatus.COMPLETED, message, null);
    }

    public static AnalysisStageResult reused(AnalysisStage stage, String message) {
        return new AnalysisStageResult(stage, AnalysisStageStatus.REUSED, message, null);
    }

    public static AnalysisStageResult skipped(
            AnalysisStage stage, AnalysisStageStatus status, String message) {
        return new AnalysisStageResult(stage, status, message, null);
    }

    public static AnalysisStageResult failed(
            AnalysisStage stage, String message, String errorCode) {
        return new AnalysisStageResult(stage, AnalysisStageStatus.FAILED, message, errorCode);
    }
}
