package com.axiom.api.controller;

import com.axiom.api.dto.GitHubPrCommentPublicationResponse;
import com.axiom.application.publication.GitHubPrTriagePublisher;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pipeline-runs")
public class GitHubPrCommentPublicationController {
    private final GitHubPrTriagePublisher publisher;

    public GitHubPrCommentPublicationController(GitHubPrTriagePublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/{id}/publish/pr-comment")
    public GitHubPrCommentPublicationResponse publish(@PathVariable UUID id) {
        return GitHubPrCommentPublicationResponse.from(publisher.publish(id));
    }
}
