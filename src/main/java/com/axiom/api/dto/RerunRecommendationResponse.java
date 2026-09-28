package com.axiom.api.dto;

import com.axiom.domain.triage.PipelineTriageResult;
import com.axiom.domain.triage.RerunRecommendation;
import java.util.List;
import java.util.UUID;

public record RerunRecommendationResponse(
        UUID pipelineRunId,
        RerunRecommendation recommendation,
        double confidence,
        String triageVersion,
        List<PipelineTriageResponse.EvidenceResponse> evidence) {
    public static RerunRecommendationResponse from(PipelineTriageResult result) {
        return new RerunRecommendationResponse(
                result.pipelineRunId(),
                result.rerunRecommendation(),
                result.rerunConfidence(),
                result.triageVersion(),
                result.evidence().stream()
                        .filter(evidence -> evidence.code().contains("RERUN")
                                || evidence.code().contains("TRANSIENT")
                                || evidence.code().contains("CHANGE_")
                                || evidence.code().contains("FAILURE_PREDATES")
                                || evidence.code().contains("CONSISTENT")
                                || evidence.code().contains("DETERMINISTIC"))
                        .map(PipelineTriageResponse.EvidenceResponse::from)
                        .toList());
    }
}
