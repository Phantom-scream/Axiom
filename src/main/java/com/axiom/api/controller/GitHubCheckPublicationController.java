package com.axiom.api.controller;

import com.axiom.api.dto.GitHubCheckPublicationResponse;
import com.axiom.application.publication.GitHubTriageCheckPublisher;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class GitHubCheckPublicationController {
    private final GitHubTriageCheckPublisher publisher;

    public GitHubCheckPublicationController(GitHubTriageCheckPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/{id}/publish/github-check")
    public GitHubCheckPublicationResponse publish(@PathVariable UUID id) {
        return GitHubCheckPublicationResponse.from(publisher.publish(id));
    }
}
