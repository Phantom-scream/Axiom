package com.axiom.api.controller;

import com.axiom.api.dto.DemoAnalysisRequest;
import com.axiom.application.analysis.DemoAnalysisService;
import com.axiom.domain.diagnosis.AnalysisResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bootstrap endpoint only; no external CI provider is invoked. */
@RestController
@RequestMapping("/api/v1/analysis")
public class DemoAnalysisController {
    private final DemoAnalysisService demoAnalysisService;
    public DemoAnalysisController(DemoAnalysisService demoAnalysisService) { this.demoAnalysisService = demoAnalysisService; }
    @PostMapping("/demo") public AnalysisResult analyze(@Valid @RequestBody DemoAnalysisRequest request) { return demoAnalysisService.analyze(request.log()); }
}
