package com.axiom.api.controller;

import com.axiom.api.dto.PipelineAnalysisResponse;
import com.axiom.application.analysis.PipelineAnalysisOrchestrator;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class PipelineAnalysisController {
    private final PipelineAnalysisOrchestrator orchestrator;

    public PipelineAnalysisController(PipelineAnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{id}/analyze")
    public PipelineAnalysisResponse analyze(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean recompute) {
        return PipelineAnalysisResponse.from(orchestrator.analyze(id, recompute));
    }
}
