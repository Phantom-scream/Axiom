package com.axiom.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.pipeline.GitChangeSet;
import com.axiom.domain.pipeline.GitProvider;
import com.axiom.integrations.github.GitHubChangeProvider;
import com.axiom.integrations.github.client.GitHubChecksClient;
import com.axiom.integrations.github.client.GitHubPullRequestClient;
import com.axiom.integrations.github.dto.GitHubCheckRunResponseDto;
import com.axiom.integrations.github.dto.GitHubIssueCommentResponseDto;
import com.axiom.integrations.github.exception.GitHubPermissionException;
import com.axiom.logstorage.LogStorage;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PipelineAnalysisAndGitHubCheckControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private LogStorage logs;
    @MockitoBean private GitHubChangeProvider changeProvider;
    @MockitoBean private GitHubChecksClient checksClient;
    @MockitoBean private GitHubPullRequestClient pullRequestClient;

    @Test
    void analyzesAllAvailableEvidenceAndReusesItWithoutPublishing() throws Exception {
        UUID runId = run(true);
        logs.store(
                runId,
                "java.net.ConnectException: Connection refused localhost:5432\n\tat app.Db.open(Db.java:91)"
                        .getBytes(StandardCharsets.UTF_8));
        when(changeProvider.providerType())
                .thenReturn(com.axiom.domain.pipeline.CiProviderType.GITHUB_ACTIONS);
        when(changeProvider.fetchChanges(any())).thenAnswer(invocation -> {
            var reference = (com.axiom.domain.pipeline.GitChangeReference) invocation.getArgument(0);
            return new GitChangeSet(
                    reference.pipelineRunId(),
                    reference.repositoryId(),
                    reference.baseSha(),
                    reference.headSha(),
                    reference.pullRequestNumber(),
                    GitProvider.GITHUB,
                    1,
                    1,
                    0,
                    List.of(new ChangedFile(
                            "README.md",
                            null,
                            ChangeType.MODIFIED,
                            FileCategory.UNKNOWN,
                            null,
                            1,
                            0,
                            1)));
        });

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/analyze", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recompute").value(false))
                .andExpect(jsonPath("$.stages[0].stage").value("LOG_PROCESSING"))
                .andExpect(jsonPath("$.stages[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.stages[2].status").value("SKIPPED_NO_DATA"))
                .andExpect(jsonPath("$.stages[4].status").value("COMPLETED"))
                .andExpect(jsonPath("$.stages[5].status").value("COMPLETED"))
                .andExpect(jsonPath("$.stages[6].status").value("COMPLETED"))
                .andExpect(jsonPath("$.triageAvailable").value(true));

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/analyze", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].status").value("REUSED"))
                .andExpect(jsonPath("$.stages[1].status").value("REUSED"))
                .andExpect(jsonPath("$.stages[4].status").value("REUSED"))
                .andExpect(jsonPath("$.stages[5].status").value("REUSED"))
                .andExpect(jsonPath("$.stages[6].status").value("REUSED"));

        verify(changeProvider, times(1)).fetchChanges(any());
        verifyNoInteractions(checksClient);
        org.assertj.core.api.Assertions.assertThat(count("failure_events", runId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(count("change_relevance_results", runId))
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(count("pipeline_triage_results", runId))
                .isEqualTo(1);
    }

    @Test
    void recomputeTrueRunsAgainWithoutDuplicatingDerivedRows() throws Exception {
        UUID runId = run(true);
        logs.store(runId, "BUILD FAILED".getBytes(StandardCharsets.UTF_8));
        when(changeProvider.providerType())
                .thenReturn(com.axiom.domain.pipeline.CiProviderType.GITHUB_ACTIONS);
        when(changeProvider.fetchChanges(any())).thenAnswer(invocation -> {
            var reference = (com.axiom.domain.pipeline.GitChangeReference) invocation.getArgument(0);
            return new GitChangeSet(
                    reference.pipelineRunId(),
                    reference.repositoryId(),
                    reference.baseSha(),
                    reference.headSha(),
                    null,
                    GitProvider.GITHUB,
                    0,
                    0,
                    0,
                    List.of());
        });

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/analyze", runId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/analyze", runId)
                        .queryParam("recompute", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recompute").value(true))
                .andExpect(jsonPath("$.stages[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.stages[6].status").value("COMPLETED"));

        verify(changeProvider, times(2)).fetchChanges(any());
        org.assertj.core.api.Assertions.assertThat(count("failure_events", runId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(count("pipeline_triage_results", runId))
                .isEqualTo(1);
    }

    @Test
    void analyzesPartialEvidenceAndReturnsNotFoundForMissingRun() throws Exception {
        UUID runId = run(false);

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/analyze", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stages[0].status").value("SKIPPED_NO_DATA"))
                .andExpect(jsonPath("$.stages[4].status").value("SKIPPED_NOT_APPLICABLE"))
                .andExpect(jsonPath("$.stages[5].status").value("SKIPPED_NO_DATA"))
                .andExpect(jsonPath("$.stages[6].status").value("COMPLETED"))
                .andExpect(jsonPath("$.triageAvailable").value(true));

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/analyze", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        verify(changeProvider, never()).fetchChanges(any());
    }

    @Test
    void explicitlyCreatesThenUpdatesOnePersistedGitHubCheck() throws Exception {
        UUID runId = run(false);
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", runId))
                .andExpect(status().isOk());
        when(checksClient.create(anyString(), anyString(), anyString(), any()))
                .thenReturn(new GitHubCheckRunResponseDto(123, "https://github.test/checks/123"));
        when(checksClient.update(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(123L), any()))
                .thenReturn(new GitHubCheckRunResponseDto(123, "https://github.test/checks/123"));

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/github-check", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalCheckRunId").value("123"))
                .andExpect(jsonPath("$.operation").value("CREATED"));
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/github-check", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("UPDATED"));

        verify(checksClient).create(
                org.mockito.ArgumentMatchers.eq("analysis-owner"),
                anyString(),
                org.mockito.ArgumentMatchers.eq("head-sha"),
                any());
        verify(checksClient).update(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(123L), any());
        org.assertj.core.api.Assertions.assertThat(count("github_publications", runId)).isEqualTo(1);
    }

    @Test
    void publishingRequiresTriageAndTranslatesProviderPermissionFailure() throws Exception {
        UUID missingTriageRun = run(false);
        mockMvc.perform(post(
                        "/api/v1/pipeline-runs/{id}/publish/github-check", missingTriageRun))
                .andExpect(status().isNotFound());

        UUID deniedRun = run(false);
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", deniedRun))
                .andExpect(status().isOk());
        when(checksClient.create(anyString(), anyString(), anyString(), any()))
                .thenThrow(new GitHubPermissionException());
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/github-check", deniedRun))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("GITHUB_PERMISSION_DENIED"));
    }

    @Test
    void explicitlyCreatesThenUpdatesOneMarkedPullRequestComment() throws Exception {
        UUID runId = run(false, 42L);
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", runId))
                .andExpect(status().isOk());
        when(pullRequestClient.createComment(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(42L), anyString()))
                .thenReturn(new GitHubIssueCommentResponseDto(
                        456, "https://github.test/comments/456"));
        when(pullRequestClient.updateComment(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(456L), anyString()))
                .thenReturn(new GitHubIssueCommentResponseDto(
                        456, "https://github.test/comments/456"));

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/pr-comment", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pullRequestNumber").value(42))
                .andExpect(jsonPath("$.externalCommentId").value("456"))
                .andExpect(jsonPath("$.operation").value("CREATED"));
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/pr-comment", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("UPDATED"));

        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pullRequestClient).createComment(
                org.mockito.ArgumentMatchers.eq("analysis-owner"),
                anyString(),
                org.mockito.ArgumentMatchers.eq(42L),
                body.capture());
        org.assertj.core.api.Assertions.assertThat(body.getValue())
                .startsWith("<!-- axiom-ci-intelligence -->");
        verify(pullRequestClient)
                .updateComment(
                        anyString(),
                        anyString(),
                        org.mockito.ArgumentMatchers.eq(456L),
                        anyString());
        org.assertj.core.api.Assertions.assertThat(count("github_publications", runId)).isEqualTo(1);
    }

    @Test
    void pullRequestPublishingRequiresTriageAndAssociatedPullRequest() throws Exception {
        UUID missingTriage = run(false, 42L);
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/pr-comment", missingTriage))
                .andExpect(status().isNotFound());

        UUID noPullRequest = run(false);
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", noPullRequest))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/pr-comment", noPullRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ANALYSIS_PREREQUISITE_MISSING"));
        verify(pullRequestClient, never())
                .createComment(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void pullRequestProviderErrorsUseExistingTranslation() throws Exception {
        UUID runId = run(false, 42L);
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", runId))
                .andExpect(status().isOk());
        when(pullRequestClient.createComment(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(42L), anyString()))
                .thenThrow(new GitHubPermissionException());

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/pr-comment", runId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("GITHUB_PERMISSION_DENIED"));
    }

    @Test
    void laterRunOfSamePullRequestUpdatesTheTrackedComment() throws Exception {
        UUID repositoryId = repository();
        UUID firstRun = run(repositoryId, false, 77L);
        UUID laterRun = run(repositoryId, false, 77L);
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", firstRun))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", laterRun))
                .andExpect(status().isOk());
        when(pullRequestClient.createComment(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(77L), anyString()))
                .thenReturn(new GitHubIssueCommentResponseDto(
                        789, "https://github.test/comments/789"));
        when(pullRequestClient.updateComment(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(789L), anyString()))
                .thenReturn(new GitHubIssueCommentResponseDto(
                        789, "https://github.test/comments/789"));

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/pr-comment", firstRun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("CREATED"));
        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/publish/pr-comment", laterRun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("UPDATED"));

        verify(pullRequestClient, times(1))
                .createComment(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(77L), anyString());
        verify(pullRequestClient, times(1))
                .updateComment(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(789L), anyString());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                        """
                        select count(distinct p.external_id)
                        from github_publications p
                        join pipeline_runs pr on pr.id=p.pipeline_run_id
                        where pr.repository_id=? and pr.pull_request_number=77
                          and p.publication_type='PR_COMMENT'
                        """,
                        Integer.class,
                        repositoryId))
                .isEqualTo(1);
    }

    private UUID run(boolean comparisonMetadata) {
        return run(comparisonMetadata, null);
    }

    private UUID run(boolean comparisonMetadata, Long pullRequestNumber) {
        return run(repository(), comparisonMetadata, pullRequestNumber);
    }

    private UUID repository() {
        UUID repositoryId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "analysis-owner",
                "repo-" + repositoryId,
                now,
                now);
        return repositoryId;
    }

    private UUID run(UUID repositoryId, boolean comparisonMetadata, Long pullRequestNumber) {
        UUID runId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,base_sha,event_name,status,
                    conclusion,attempt,pull_request_number,ingested_at)
                values(?,?,?,?,?,?,?,?,?,?,?)
                """,
                runId,
                repositoryId,
                Math.abs(runId.getMostSignificantBits()),
                "head-sha",
                comparisonMetadata ? "base-sha" : null,
                comparisonMetadata ? "pull_request" : "workflow_dispatch",
                "COMPLETED",
                "FAILURE",
                1,
                pullRequestNumber,
                now);
        return runId;
    }

    private int count(String table, UUID pipelineRunId) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where pipeline_run_id=?",
                Integer.class,
                pipelineRunId);
    }
}
