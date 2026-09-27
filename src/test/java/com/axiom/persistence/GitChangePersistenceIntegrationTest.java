package com.axiom.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.pipeline.GitChangePersistenceService;
import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.pipeline.GitChangeSet;
import com.axiom.domain.pipeline.GitProvider;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class GitChangePersistenceIntegrationTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GitChangePersistenceService persistence;

    @Test
    void persistsAndIdempotentlyReplacesNormalizedChangedFiles() {
        RunData data = pipelineRun();
        var initial = new GitChangeSet(
                data.runId(),
                data.repositoryId(),
                "base-sha",
                "head-sha",
                17L,
                GitProvider.GITHUB,
                2,
                8,
                3,
                List.of(
                        new ChangedFile(
                                "src/main/NewName.java",
                                "src/main/OldName.java",
                                ChangeType.RENAMED,
                                FileCategory.PRODUCTION_SOURCE,
                                "src/main",
                                3,
                                2,
                                5),
                        new ChangedFile(
                                "README.md",
                                null,
                                ChangeType.MODIFIED,
                                FileCategory.DOCUMENTATION,
                                null,
                                5,
                                1,
                                6)));

        persistence.save(initial);
        var stored = persistence.findByPipelineRunId(data.runId());

        assertThat(stored.totalChangedFiles()).isEqualTo(2);
        assertThat(stored.totalAdditions()).isEqualTo(8);
        assertThat(stored.totalDeletions()).isEqualTo(3);
        assertThat(stored.files()).hasSize(2);
        assertThat(stored.files())
                .filteredOn(file -> file.changeType() == ChangeType.RENAMED)
                .singleElement()
                .extracting(ChangedFile::previousPath)
                .isEqualTo("src/main/OldName.java");

        var replacement = new GitChangeSet(
                data.runId(),
                data.repositoryId(),
                "base-sha",
                "head-sha",
                17L,
                GitProvider.GITHUB,
                1,
                1,
                0,
                List.of(new ChangedFile(
                        "package.json",
                        null,
                        ChangeType.MODIFIED,
                        FileCategory.DEPENDENCY_MANIFEST,
                        null,
                        1,
                        0,
                        1)));
        persistence.save(replacement);

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
                .isEqualTo(1);
        assertThat(persistence.findByPipelineRunId(data.runId()).files().getFirst().fileCategory())
                .isEqualTo(FileCategory.DEPENDENCY_MANIFEST);
    }

    private RunData pipelineRun() {
        UUID repositoryId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "change-owner",
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
                9100,
                "head-sha",
                "base-sha",
                "pull_request",
                17,
                "COMPLETED",
                "FAILURE",
                1,
                now);
        return new RunData(repositoryId, runId);
    }

    private record RunData(UUID repositoryId, UUID runId) {}
}
