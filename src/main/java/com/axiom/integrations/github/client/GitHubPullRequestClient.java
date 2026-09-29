package com.axiom.integrations.github.client;

import com.axiom.integrations.github.dto.GitHubIssueCommentRequestDto;
import com.axiom.integrations.github.dto.GitHubIssueCommentResponseDto;
import org.springframework.stereotype.Component;

@Component
public class GitHubPullRequestClient {
    private final GitHubApiClient api;

    public GitHubPullRequestClient(GitHubApiClient api) {
        this.api = api;
    }

    public GitHubIssueCommentResponseDto createComment(
            String owner, String repository, long pullRequestNumber, String body) {
        return api.createIssueComment(
                owner, repository, pullRequestNumber, new GitHubIssueCommentRequestDto(body));
    }

    public GitHubIssueCommentResponseDto updateComment(
            String owner, String repository, long commentId, String body) {
        return api.updateIssueComment(
                owner, repository, commentId, new GitHubIssueCommentRequestDto(body));
    }
}
