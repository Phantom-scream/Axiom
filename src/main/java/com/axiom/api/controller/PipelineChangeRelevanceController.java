package com.axiom.api.controller;

import com.axiom.api.dto.ChangeAnalysisResponse;
import com.axiom.api.dto.ChangeRelevanceResponse;
import com.axiom.application.analysis.PipelineChangeAnalysisService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class PipelineChangeRelevanceController {
    private final PipelineChangeAnalysisService analysis;

    public PipelineChangeRelevanceController(PipelineChangeAnalysisService analysis) {
        this.analysis = analysis;
    }

    @PostMapping("/{id}/changes/analyze")
    public ChangeAnalysisResponse analyze(@PathVariable UUID id) {
        return ChangeAnalysisResponse.from(analysis.analyze(id));
    }

    @GetMapping("/{id}/relevance")
    public List<ChangeRelevanceResponse> get(@PathVariable UUID id) {
        return analysis.get(id).stream().map(ChangeRelevanceResponse::from).toList();
    }

    @GetMapping("/{id}/relevance/{fingerprint}")
    public ChangeRelevanceResponse get(
            @PathVariable UUID id, @PathVariable String fingerprint) {
        return ChangeRelevanceResponse.from(analysis.get(id, fingerprint));
    }
}
