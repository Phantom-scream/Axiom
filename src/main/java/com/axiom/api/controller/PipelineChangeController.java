package com.axiom.api.controller;

import com.axiom.api.dto.GitChangeSetResponse;
import com.axiom.application.pipeline.GitChangeIngestionService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class PipelineChangeController {
    private final GitChangeIngestionService changes;

    public PipelineChangeController(GitChangeIngestionService changes) {
        this.changes = changes;
    }

    @PostMapping("/{id}/changes/ingest")
    public GitChangeSetResponse ingest(@PathVariable UUID id) {
        return GitChangeSetResponse.from(changes.ingest(id));
    }

    @GetMapping("/{id}/changes")
    public GitChangeSetResponse get(@PathVariable UUID id) {
        return GitChangeSetResponse.from(changes.get(id));
    }
}
