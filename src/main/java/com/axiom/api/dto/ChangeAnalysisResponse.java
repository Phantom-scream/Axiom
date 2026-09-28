package com.axiom.api.dto;

import com.axiom.application.analysis.PipelineChangeAnalysisService.AnalysisSummary;
import com.axiom.domain.relevance.ChangeRelevance;
import java.util.Map;
import java.util.UUID;

public record ChangeAnalysisResponse(
        UUID pipelineRunId,
        String analyzerVersion,
        int analyzedFailures,
        Map<ChangeRelevance, Integer> results) {
    public static ChangeAnalysisResponse from(AnalysisSummary summary) {
        return new ChangeAnalysisResponse(
                summary.pipelineRunId(),
                summary.analyzerVersion(),
                summary.analyzedFailures(),
                summary.results());
    }
}
