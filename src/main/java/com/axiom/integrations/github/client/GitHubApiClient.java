package com.axiom.integrations.github.client;

import com.axiom.config.GitHubProperties;
import com.axiom.integrations.github.dto.*;
import com.axiom.integrations.github.exception.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GitHubApiClient {
    private final WebClient client;
    private final GitHubProperties properties;
    public GitHubApiClient(WebClient.Builder builder, GitHubProperties properties) { this.client = builder.baseUrl(properties.resolvedBaseUrl()).defaultHeader("Accept", "application/vnd.github+json").defaultHeader("X-GitHub-Api-Version", "2022-11-28").defaultHeader("User-Agent", "Axiom-CI-Failure-Intelligence").build(); this.properties = properties; }
    public GitHubWorkflowRunDto workflowRun(String owner, String repo, long runId) { return get("/repos/{owner}/{repo}/actions/runs/{id}", GitHubWorkflowRunDto.class, owner, repo, runId); }
    public List<GitHubJobDto> jobs(String owner, String repo, long runId) { List<GitHubJobDto> all = new ArrayList<>(); for (int page = 1;;page++) { GitHubJobsResponseDto response = get("/repos/{owner}/{repo}/actions/runs/{id}/jobs?per_page=100&page={page}", GitHubJobsResponseDto.class, owner, repo, runId, page); List<GitHubJobDto> jobs = response.jobs() == null ? List.of() : response.jobs(); all.addAll(jobs); if (jobs.size() < 100 || all.size() >= response.totalCount()) return all; } }
    public byte[] logs(String owner, String repo, long runId) { return get("/repos/{owner}/{repo}/actions/runs/{id}/logs", byte[].class, owner, repo, runId); }
    private <T> T get(String path, Class<T> type, Object... variables) {
        if (properties.token() == null || properties.token().isBlank()) throw new GitHubAuthenticationException();
        return client.get().uri(path, variables).headers(h -> h.setBearerAuth(properties.token())).retrieve().onStatus(HttpStatusCode::isError, response -> response.createException().map(error -> translate(error.getStatusCode(), variables))).bodyToMono(type).block();
    }
    private RuntimeException translate(HttpStatusCode status, Object[] variables) { int code=status.value(); if(code==401)return new GitHubAuthenticationException(); if(code==403)return new GitHubPermissionException(); if(code==404)return new PipelineRunNotFoundException((Long)variables[2]); if(code>=500)return new ExternalProviderUnavailableException(); return new GitHubIntegrationException("GitHub rejected the request (HTTP " + code + ")."); }
}
