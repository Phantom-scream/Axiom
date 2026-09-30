package com.axiom.integrations.github.client;

import com.axiom.config.GitHubProperties;
import com.axiom.integrations.github.dto.GitHubCompareResponseDto;
import com.axiom.integrations.github.dto.GitHubCheckRunResponseDto;
import com.axiom.integrations.github.dto.GitHubCreateCheckRunRequestDto;
import com.axiom.integrations.github.dto.GitHubJobDto;
import com.axiom.integrations.github.dto.GitHubJobsResponseDto;
import com.axiom.integrations.github.dto.GitHubIssueCommentRequestDto;
import com.axiom.integrations.github.dto.GitHubIssueCommentResponseDto;
import com.axiom.integrations.github.dto.GitHubWorkflowRunDto;
import com.axiom.integrations.github.dto.GitHubUpdateCheckRunRequestDto;
import com.axiom.integrations.github.exception.ExternalProviderUnavailableException;
import com.axiom.integrations.github.exception.GitHubAuthenticationException;
import com.axiom.integrations.github.exception.GitHubCheckTargetNotFoundException;
import com.axiom.integrations.github.exception.GitHubComparisonNotFoundException;
import com.axiom.integrations.github.exception.GitHubIntegrationException;
import com.axiom.integrations.github.exception.GitHubPermissionException;
import com.axiom.integrations.github.exception.GitHubPullRequestNotFoundException;
import com.axiom.integrations.github.exception.GitHubRateLimitException;
import com.axiom.integrations.github.exception.InvalidGitHubComparisonException;
import com.axiom.integrations.github.exception.InvalidGitHubCheckException;
import com.axiom.integrations.github.exception.InvalidGitHubCommentException;
import com.axiom.integrations.github.exception.PipelineRunNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import com.axiom.observability.AxiomMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@Component
public class GitHubApiClient {
    private final WebClient client;
    private final GitHubProperties properties;
    private final AxiomMetrics metrics;

    @Autowired
    public GitHubApiClient(
            WebClient.Builder builder, GitHubProperties properties, AxiomMetrics metrics) {
        this.client = builder.baseUrl(properties.resolvedBaseUrl())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "Axiom-CI-Failure-Intelligence")
                .build();
        this.properties = properties;
        this.metrics = metrics;
    }

    public GitHubApiClient(WebClient.Builder builder, GitHubProperties properties) {
        this(builder, properties, new AxiomMetrics(new SimpleMeterRegistry()));
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

    public GitHubWorkflowRunDto workflowRunAttempt(String owner, String repo, long runId, int attempt) {
        return get("/repos/{owner}/{repo}/actions/runs/{id}/attempts/{attempt}",
                GitHubWorkflowRunDto.class, NotFoundKind.PIPELINE_RUN, owner, repo, runId, attempt);
    }

    public List<GitHubJobDto> attemptJobs(String owner, String repo, long runId, int attempt) {
        List<GitHubJobDto> all = new ArrayList<>();
        for (int page = 1; page <= 100; page++) {
            var response = get("/repos/{owner}/{repo}/actions/runs/{id}/attempts/{attempt}/jobs?per_page=100&page={page}",
                    GitHubJobsResponseDto.class, NotFoundKind.PIPELINE_RUN, owner, repo, runId, attempt, page);
            var jobs = response.jobs() == null ? List.<GitHubJobDto>of() : response.jobs();
            all.addAll(jobs);
            if (jobs.size() < 100 || all.size() >= response.totalCount()) return List.copyOf(all);
        }
        throw new GitHubIntegrationException("GitHub job collection exceeds the supported bound.");
    }

    public byte[] attemptLogs(String owner, String repo, long runId, int attempt) {
        return get("/repos/{owner}/{repo}/actions/runs/{id}/attempts/{attempt}/logs",
                byte[].class, NotFoundKind.PIPELINE_RUN, owner, repo, runId, attempt);
    }

    public List<GitHubJobDto> jobs(String owner, String repo, long runId) {
        List<GitHubJobDto> all = new ArrayList<>();
        for (int page = 1; page <= 100; page++) {
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
        throw new GitHubIntegrationException("GitHub job collection exceeds the supported bound.");
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
                NotFoundKind.CHECK,
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
                NotFoundKind.CHECK,
                owner,
                repo,
                checkRunId);
    }

    public GitHubIssueCommentResponseDto createIssueComment(
            String owner, String repo, long pullRequestNumber, GitHubIssueCommentRequestDto request) {
        return write(
                HttpMethod.POST,
                "/repos/{owner}/{repo}/issues/{pullRequestNumber}/comments",
                request,
                GitHubIssueCommentResponseDto.class,
                NotFoundKind.COMMENT,
                owner,
                repo,
                pullRequestNumber);
    }

    public GitHubIssueCommentResponseDto updateIssueComment(
            String owner, String repo, long commentId, GitHubIssueCommentRequestDto request) {
        return write(
                HttpMethod.PATCH,
                "/repos/{owner}/{repo}/issues/comments/{commentId}",
                request,
                GitHubIssueCommentResponseDto.class,
                NotFoundKind.COMMENT,
                owner,
                repo,
                commentId);
    }

    private <T> T get(
            String path, Class<T> type, NotFoundKind notFoundKind, Object... variables) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new GitHubAuthenticationException();
        }
        String operation = "get_" + notFoundKind.name().toLowerCase();
        return withRetry(operation, true, () -> requestGet(path, type, notFoundKind, variables));
    }

    private <T> T requestGet(
            String path, Class<T> type, NotFoundKind notFoundKind, Object[] variables) {
        try {
            metrics.githubRequest("get_" + notFoundKind.name().toLowerCase(), "attempt");
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
                    .block(properties.effectiveRequestTimeout());
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
            HttpMethod method,
            String path,
            Object body,
            Class<T> type,
            NotFoundKind notFoundKind,
            Object... variables) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new GitHubAuthenticationException();
        }
        String operation = method.name().toLowerCase() + "_" + notFoundKind.name().toLowerCase();
        return withRetry(
                operation,
                method == HttpMethod.PATCH,
                () -> requestWrite(method, path, body, type, notFoundKind, variables));
    }

    private <T> T requestWrite(
            HttpMethod method,
            String path,
            Object body,
            Class<T> type,
            NotFoundKind notFoundKind,
            Object[] variables) {
        try {
            metrics.githubRequest(
                    method.name().toLowerCase() + "_" + notFoundKind.name().toLowerCase(),
                    "attempt");
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
                                            notFoundKind,
                                            variables)))
                    .bodyToMono(type)
                    .block(properties.effectiveRequestTimeout());
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

    private <T> T withRetry(String operation, boolean safeToRetry, Supplier<T> request) {
        int attempts = safeToRetry ? properties.effectiveRetryMaxAttempts() : 1;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T result = request.get();
                metrics.githubRequest(operation, "success");
                return result;
            } catch (RuntimeException exception) {
                last = exception;
                String category = errorCategory(exception);
                metrics.githubError(operation, category);
                if (exception instanceof GitHubRateLimitException) {
                    metrics.githubRateLimit(operation);
                }
                if (attempt == attempts || !retryable(exception)) throw exception;
                sleep(backoff(attempt, exception));
            }
        }
        throw last;
    }

    private boolean retryable(RuntimeException exception) {
        if (exception instanceof GitHubRateLimitException rateLimit) {
            Duration required = rateLimit.retryAfter();
            if (required == null && rateLimit.resetAt() != null) {
                required = Duration.between(Instant.now(), rateLimit.resetAt());
            }
            return required != null && required.compareTo(properties.effectiveRetryMaxBackoff()) <= 0;
        }
        return exception instanceof ExternalProviderUnavailableException unavailable && unavailable.retryable();
    }

    private Duration backoff(int attempt, RuntimeException exception) {
        if (exception instanceof GitHubRateLimitException rateLimit
                && rateLimit.retryAfter() != null) {
            return min(rateLimit.retryAfter(), properties.effectiveRetryMaxBackoff());
        }
        if (exception instanceof GitHubRateLimitException rateLimit && rateLimit.resetAt() != null) {
            Duration required = Duration.between(Instant.now(), rateLimit.resetAt());
            return required.isNegative() ? Duration.ZERO : required;
        }
        long initial = properties.effectiveRetryInitialBackoff().toMillis();
        long maximum = properties.effectiveRetryMaxBackoff().toMillis();
        long exponential = Math.min(maximum, initial * (1L << Math.min(attempt - 1, 10)));
        long jitter = exponential <= 1 ? 0 : ThreadLocalRandom.current().nextLong(exponential / 2 + 1);
        return Duration.ofMillis(Math.min(maximum, exponential / 2 + jitter));
    }

    private Duration min(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private void sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalProviderUnavailableException();
        }
    }

    private String errorCategory(RuntimeException exception) {
        if (exception instanceof GitHubRateLimitException) return "rate_limit";
        if (exception instanceof GitHubAuthenticationException) return "authentication";
        if (exception instanceof GitHubPermissionException) return "permission";
        if (exception instanceof ExternalProviderUnavailableException) return "unavailable";
        return "request";
    }

    private RuntimeException translate(
            HttpStatusCode status,
            HttpHeaders headers,
            NotFoundKind notFoundKind,
            Object[] variables) {
        int code = status.value();
        if (code == 401) return new GitHubAuthenticationException();
        if (code == 429
                || (code == 403
                        && ("0".equals(headers.getFirst("X-RateLimit-Remaining"))
                                || headers.containsHeader(HttpHeaders.RETRY_AFTER)))) {
            return new GitHubRateLimitException(retryAfter(headers), resetAt(headers));
        }
        if (code == 403) return new GitHubPermissionException();
        if (code == 404 && notFoundKind == NotFoundKind.PIPELINE_RUN) {
            return new PipelineRunNotFoundException((Long) variables[2]);
        }
        if (code == 404 && notFoundKind == NotFoundKind.CHECK) {
            return new GitHubCheckTargetNotFoundException();
        }
        if (code == 404 && notFoundKind == NotFoundKind.COMMENT) {
            return new GitHubPullRequestNotFoundException();
        }
        if (code == 404) return new GitHubComparisonNotFoundException();
        if (code == 422 && notFoundKind == NotFoundKind.CHECK) {
            return new InvalidGitHubCheckException();
        }
        if (code == 422 && notFoundKind == NotFoundKind.COMMENT) {
            return new InvalidGitHubCommentException();
        }
        if (code == 422) return new InvalidGitHubComparisonException();
        if (code == 502 || code == 503 || code == 504) return new ExternalProviderUnavailableException();
        if (code >= 500) return new ExternalProviderUnavailableException(false);
        return new GitHubIntegrationException("GitHub rejected the request (HTTP " + code + ").");
    }

    private Duration retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) return null;
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            try {
                Instant date = java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration delay = Duration.between(Instant.now(), date);
                return delay.isNegative() ? Duration.ZERO : delay;
            } catch (java.time.format.DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private Instant resetAt(HttpHeaders headers) {
        String value = headers.getFirst("X-RateLimit-Reset");
        if (value == null) return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private enum NotFoundKind {
        PIPELINE_RUN,
        COMPARISON,
        CHECK,
        COMMENT
    }
}
