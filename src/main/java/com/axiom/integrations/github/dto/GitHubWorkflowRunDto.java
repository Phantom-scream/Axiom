package com.axiom.integrations.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWorkflowRunDto(long id, String status, String conclusion, String event, @JsonProperty("head_sha") String headSha, @JsonProperty("head_branch") String headBranch, @JsonProperty("run_attempt") int runAttempt, @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt, GitHubRepositoryDto repository, @JsonProperty("pull_requests") List<GitHubPullRequestRefDto> pullRequests) {}
