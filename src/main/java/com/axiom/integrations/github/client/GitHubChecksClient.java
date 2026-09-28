package com.axiom.integrations.github.client;

import com.axiom.integrations.github.dto.GitHubCheckOutputDto;
import com.axiom.integrations.github.dto.GitHubCheckRunResponseDto;
import com.axiom.integrations.github.dto.GitHubCreateCheckRunRequestDto;
import com.axiom.integrations.github.dto.GitHubUpdateCheckRunRequestDto;
import org.springframework.stereotype.Component;

@Component
public class GitHubChecksClient {
    public static final String CHECK_NAME = "Axiom CI Intelligence";
    private final GitHubApiClient api;

    public GitHubChecksClient(GitHubApiClient api) {
        this.api = api;
    }

    public GitHubCheckRunResponseDto create(
            String owner, String repository, String headSha, CheckOutput output) {
        return api.createCheckRun(
                owner,
                repository,
                new GitHubCreateCheckRunRequestDto(
                        CHECK_NAME,
                        headSha,
                        "completed",
                        "neutral",
                        new GitHubCheckOutputDto(output.title(), output.summary(), output.text())));
    }

    public GitHubCheckRunResponseDto update(
            String owner, String repository, long checkRunId, CheckOutput output) {
        return api.updateCheckRun(
                owner,
                repository,
                checkRunId,
                new GitHubUpdateCheckRunRequestDto(
                        CHECK_NAME,
                        "completed",
                        "neutral",
                        new GitHubCheckOutputDto(output.title(), output.summary(), output.text())));
    }

    public record CheckOutput(String title, String summary, String text) {}
}
