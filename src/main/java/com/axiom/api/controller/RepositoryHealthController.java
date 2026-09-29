package com.axiom.api.controller;

import com.axiom.api.dto.RepositoryHealthResponse;
import com.axiom.application.health.RepositoryHealthService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoryHealthController {
    private final RepositoryHealthService health;

    public RepositoryHealthController(RepositoryHealthService health) {
        this.health = health;
    }

    @GetMapping("/{repositoryId}/health")
    public RepositoryHealthResponse get(
            @PathVariable UUID repositoryId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "200") int maxRuns) {
        return RepositoryHealthResponse.from(health.get(repositoryId, days, maxRuns));
    }
}
