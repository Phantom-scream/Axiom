package com.axiom.api.controller;

import com.axiom.api.dto.PipelineTriageResponse;
import com.axiom.api.dto.RerunRecommendationResponse;
import com.axiom.application.analysis.PipelineTriageApplicationService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class PipelineTriageController {
    private final PipelineTriageApplicationService triage;

    public PipelineTriageController(PipelineTriageApplicationService triage) {
        this.triage = triage;
    }

    @PostMapping("/{id}/triage")
    public PipelineTriageResponse compute(@PathVariable UUID id) {
        return PipelineTriageResponse.from(triage.compute(id));
    }

    @GetMapping("/{id}/triage")
    public PipelineTriageResponse get(@PathVariable UUID id) {
        return PipelineTriageResponse.from(triage.get(id));
    }

    @GetMapping("/{id}/triage/failures")
    public List<PipelineTriageResponse.FailureResponse> failures(@PathVariable UUID id) {
        return PipelineTriageResponse.from(triage.get(id)).failures();
    }

    @GetMapping("/{id}/triage/actions")
    public List<PipelineTriageResponse.ActionResponse> actions(@PathVariable UUID id) {
        return PipelineTriageResponse.from(triage.get(id)).actions();
    }

    @GetMapping("/{id}/rerun-recommendation")
    public RerunRecommendationResponse rerunRecommendation(@PathVariable UUID id) {
        return RerunRecommendationResponse.from(triage.get(id));
    }
}
