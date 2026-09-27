package com.axiom.application.analysis;

import com.axiom.domain.pipeline.PipelineRun;
import java.util.Objects;
import java.util.Optional;

/** Normalized analysis input; future test, Git, and history signals belong here, not provider adapters. */
public record AnalysisContext(PipelineRun pipelineRun, String logs) {
    public AnalysisContext { Objects.requireNonNull(pipelineRun); logs = logs == null ? "" : logs; }
    public Optional<String> logsIfPresent() { return logs.isBlank() ? Optional.empty() : Optional.of(logs); }
}
