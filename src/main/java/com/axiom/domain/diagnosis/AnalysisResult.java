package com.axiom.domain.diagnosis;

import com.axiom.domain.pipeline.PipelineRun;
import java.time.Instant;

public record AnalysisResult(PipelineRun pipelineRun, Diagnosis diagnosis, Instant analyzedAt) {}
