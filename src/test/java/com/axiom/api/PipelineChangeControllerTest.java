package com.axiom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.pipeline.GitChangeReference;
import com.axiom.domain.pipeline.GitChangeSet;
import com.axiom.domain.pipeline.GitProvider;
import com.axiom.integrations.github.GitHubChangeProvider;
import com.axiom.integrations.github.exception.GitHubPermissionException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PipelineChangeControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private GitHubChangeProvider provider;

    @BeforeEach
    void providerType() {
        when(provider.providerType()).thenReturn(CiProviderType.GITHUB_ACTIONS);
    }

    @Test
    void postIngestsAndGetReadsPersistedNormalizedChangesWithoutCallingGitHub() throws Exception {
        RunData data = pipelineRun("base", "head", 23L);
        when(provider.fetchChanges(any(GitChangeReference.class)))
                .thenReturn(new GitChangeSet(
                        data.runId(),
                        data.repositoryId(),
                        "base",
                        "head",
                        23L,
                        GitProvider.GITHUB,
                        2,
                        9,
                        3,
                        List.of(
                                new ChangedFile(
                                        "src/main/java/com/example/PaymentService.java",
                                        null,
                                        ChangeType.MODIFIED,
                                        FileCategory.UNKNOWN,
                                        null,
                                        7,
                                        2,
                                        9),
                                new ChangedFile(
                                        ".github/workflows/ci.yml",
                                        ".github/workflows/build.yml",
                                        ChangeType.RENAMED,
                                        FileCategory.UNKNOWN,
                                        null,
                                        2,
                                        1,
                                        3))));

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/changes/ingest", data.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineRunId").value(data.runId().toString()))
                .andExpect(jsonPath("$.baseSha").value("base"))
                .andExpect(jsonPath("$.headSha").value("head"))
                .andExpect(jsonPath("$.provider").value("GITHUB"))
                .andExpect(jsonPath("$.totalChangedFiles").value(2))
                .andExpect(jsonPath("$.files[0].fileCategory").value("PRODUCTION_SOURCE"))
                .andExpect(jsonPath("$.files[0].moduleHint").value("src/main/java/com/example"))
                .andExpect(jsonPath("$.files[1].fileCategory").value("CI_CONFIGURATION"))
                .andExpect(jsonPath("$.files[1].previousPath")
                        .value(".github/workflows/build.yml"));

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/changes/ingest", data.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChangedFiles").value(2));
        assertThat(jdbc.queryForObject(
                        "select count(*) from git_change_sets where pipeline_run_id=?",
                        Integer.class,
                        data.runId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        """
                        select count(*) from changed_files f
                        join git_change_sets g on g.id=f.change_set_id
                        where g.pipeline_run_id=?
                        """,
                        Integer.class,
                        data.runId()))
                .isEqualTo(2);

        clearInvocations(provider);
        mockMvc.perform(get("/api/v1/pipeline-runs/{id}/changes", data.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAdditions").value(9))
                .andExpect(jsonPath("$.totalDeletions").value(3))
                .andExpect(jsonPath("$.files.length()").value(2));
        verifyNoInteractions(provider);
    }

    @Test
    void missingPipelineAndMissingBaseReturnControlledErrors() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/pipeline-runs/{id}/changes/ingest", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        RunData withoutBase = pipelineRun(null, "head", null);
        mockMvc.perform(post(
                        "/api/v1/pipeline-runs/{id}/changes/ingest", withoutBase.runId()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("GIT_COMPARISON_UNAVAILABLE"));
    }

    @Test
    void providerErrorsUseCentralTranslation() throws Exception {
        RunData data = pipelineRun("base", "head", null);
        when(provider.fetchChanges(any(GitChangeReference.class)))
                .thenThrow(new GitHubPermissionException());

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/changes/ingest", data.runId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("GITHUB_PERMISSION_DENIED"));
    }

    private RunData pipelineRun(String baseSha, String headSha, Long pullRequestNumber) {
        UUID repositoryId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "api-change-owner",
                "repo-" + repositoryId,
                now,
                now);
        jdbc.update(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,base_sha,event_name,
                    pull_request_number,status,conclusion,attempt,ingested_at)
                values(?,?,?,?,?,?,?,?,?,?,?)
                """,
                runId,
                repositoryId,
                Math.abs(runId.getMostSignificantBits()),
                headSha,
                baseSha,
                pullRequestNumber == null ? "push" : "pull_request",
                pullRequestNumber,
                "COMPLETED",
                "FAILURE",
                1,
                now);
        return new RunData(repositoryId, runId);
    }

    private record RunData(UUID repositoryId, UUID runId) {}
}
