package com.axiom.integrations.github.client;

import com.axiom.config.GitHubProperties;
import com.axiom.integrations.github.dto.GitHubCompareResponseDto;
import com.axiom.integrations.github.dto.GitHubCheckRunResponseDto;
import com.axiom.integrations.github.dto.GitHubCreateCheckRunRequestDto;
import com.axiom.integrations.github.dto.GitHubJobDto;
import com.axiom.integrations.github.dto.GitHubJobsResponseDto;
import com.axiom.integrations.github.dto.GitHubWorkflowRunDto;
import com.axiom.integrations.github.dto.GitHubUpdateCheckRunRequestDto;
import com.axiom.integrations.github.exception.ExternalProviderUnavailableException;
import com.axiom.integrations.github.exception.GitHubAuthenticationException;
import com.axiom.integrations.github.exception.GitHubCheckTargetNotFoundException;
import com.axiom.integrations.github.exception.GitHubComparisonNotFoundException;
import com.axiom.integrations.github.exception.GitHubIntegrationException;
import com.axiom.integrations.github.exception.GitHubPermissionException;
import com.axiom.integrations.github.exception.GitHubRateLimitException;
import com.axiom.integrations.github.exception.InvalidGitHubComparisonException;
import com.axiom.integrations.github.exception.InvalidGitHubCheckException;
import com.axiom.integrations.github.exception.PipelineRunNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Component
public class GitHubApiClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private final WebClient client;
    private final GitHubProperties properties;

    public GitHubApiClient(WebClient.Builder builder, GitHubProperties properties) {
        this.client = builder.baseUrl(properties.resolvedBaseUrl())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "Axiom-CI-Failure-Intelligence")
                .build();
        this.properties = properties;
    }

    public GitHubWorkflowRunDto workflowRun(String owner, String repo, long runId) {
        return get(
                "/repos/{owner}/{repo}/actions/runs/{id}",
                GitHubWorkflowRunDto.class,
                NotFoundKind.PIPELINE_RUN,
                owner,
                repo,
                runId);
    }

    public List<GitHubJobDto> jobs(String owner, String repo, long runId) {
        List<GitHubJobDto> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            GitHubJobsResponseDto response = get(
                    "/repos/{owner}/{repo}/actions/runs/{id}/jobs?per_page=100&page={page}",
                    GitHubJobsResponseDto.class,
                    NotFoundKind.PIPELINE_RUN,
                    owner,
                    repo,
                    runId,
                    page);
            List<GitHubJobDto> jobs = response.jobs() == null ? List.of() : response.jobs();
            all.addAll(jobs);
            if (jobs.size() < 100 || all.size() >= response.totalCount()) return List.copyOf(all);
        }
    }

    public byte[] logs(String owner, String repo, long runId) {
        return get(
                "/repos/{owner}/{repo}/actions/runs/{id}/logs",
                byte[].class,
                NotFoundKind.PIPELINE_RUN,
                owner,
                repo,
                runId);
    }

    public GitHubCompareResponseDto compare(String owner, String repo, String base, String head) {
        return get(
                "/repos/{owner}/{repo}/compare/{base}...{head}",
                GitHubCompareResponseDto.class,
                NotFoundKind.COMPARISON,
                owner,
                repo,
                base,
                head);
    }

    public GitHubCheckRunResponseDto createCheckRun(
            String owner, String repo, GitHubCreateCheckRunRequestDto request) {
        return write(
                HttpMethod.POST,
                "/repos/{owner}/{repo}/check-runs",
                request,
                GitHubCheckRunResponseDto.class,
                owner,
                repo);
    }

    public GitHubCheckRunResponseDto updateCheckRun(
            String owner, String repo, long checkRunId, GitHubUpdateCheckRunRequestDto request) {
        return write(
                HttpMethod.PATCH,
                "/repos/{owner}/{repo}/check-runs/{checkRunId}",
                request,
                GitHubCheckRunResponseDto.class,
                owner,
                repo,
                checkRunId);
    }

    private <T> T get(
            String path, Class<T> type, NotFoundKind notFoundKind, Object... variables) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new GitHubAuthenticationException();
        }
        try {
            return client.get()
                    .uri(path, variables)
                    .headers(headers -> headers.setBearerAuth(properties.token()))
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            response -> response.createException().map(error -> translate(
                                            error.getStatusCode(),
                                            response.headers().asHttpHeaders(),
                                            notFoundKind,
                                            variables)))
                    .bodyToMono(type)
                    .block(REQUEST_TIMEOUT);
        } catch (GitHubIntegrationException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            throw new ExternalProviderUnavailableException();
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("Timeout on blocking read")) {
                throw new ExternalProviderUnavailableException();
            }
            throw exception;
        }
    }

    private <T> T write(
            HttpMethod method, String path, Object body, Class<T> type, Object... variables) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new GitHubAuthenticationException();
        }
        try {
            return client.method(method)
                    .uri(path, variables)
                    .headers(headers -> headers.setBearerAuth(properties.token()))
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            response -> response.createException().map(error -> translate(
                                            error.getStatusCode(),
                                            response.headers().asHttpHeaders(),
                                            NotFoundKind.CHECK,
                                            variables)))
                    .bodyToMono(type)
                    .block(REQUEST_TIMEOUT);
        } catch (GitHubIntegrationException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            throw new ExternalProviderUnavailableException();
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("Timeout on blocking read")) {
                throw new ExternalProviderUnavailableException();
            }
            throw exception;
        }
    }

    private RuntimeException translate(
            HttpStatusCode status,
            HttpHeaders headers,
            NotFoundKind notFoundKind,
            Object[] variables) {
        int code = status.value();
        if (code == 401) return new GitHubAuthenticationException();
        if (code == 403 && "0".equals(headers.getFirst("X-RateLimit-Remaining"))) {
            return new GitHubRateLimitException();
        }
        if (code == 403) return new GitHubPermissionException();
        if (code == 404 && notFoundKind == NotFoundKind.PIPELINE_RUN) {
            return new PipelineRunNotFoundException((Long) variables[2]);
        }
        if (code == 404 && notFoundKind == NotFoundKind.CHECK) {
            return new GitHubCheckTargetNotFoundException();
        }
        if (code == 404) return new GitHubComparisonNotFoundException();
        if (code == 422 && notFoundKind == NotFoundKind.CHECK) {
            return new InvalidGitHubCheckException();
        }
        if (code == 422) return new InvalidGitHubComparisonException();
        if (code >= 500) return new ExternalProviderUnavailableException();
        return new GitHubIntegrationException("GitHub rejected the request (HTTP " + code + ").");
    }

    private enum NotFoundKind {
        PIPELINE_RUN,
        COMPARISON,
        CHECK
    }
}
