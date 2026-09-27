package com.axiom.application.pipeline;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.FileCategory;
import com.axiom.domain.pipeline.GitChangeSet;
import com.axiom.domain.pipeline.GitProvider;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitChangePersistenceService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GitChangePersistenceService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public GitChangeSet save(GitChangeSet changeSet) {
        UUID changeSetId = jdbc.query(
                """
                insert into git_change_sets(
                    id,pipeline_run_id,base_sha,head_sha,total_changed_files,provider,ingested_at)
                values(?,?,?,?,?,?,?)
                on conflict(pipeline_run_id,base_sha,head_sha) do update set
                    total_changed_files=excluded.total_changed_files,
                    provider=excluded.provider,
                    ingested_at=excluded.ingested_at
                returning id
                """,
                rs -> {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                },
                UUID.randomUUID(),
                changeSet.pipelineRunId(),
                changeSet.baseSha(),
                changeSet.headSha(),
                changeSet.totalChangedFiles(),
                changeSet.provider().name(),
                Timestamp.from(clock.instant()));
        jdbc.update("delete from changed_files where change_set_id=?", changeSetId);
        for (ChangedFile file : changeSet.files()) {
            jdbc.update(
                    """
                    insert into changed_files(
                        id,change_set_id,path,previous_path,change_type,file_category,
                        module_hint,additions,deletions,changes)
                    values(?,?,?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    changeSetId,
                    file.path(),
                    file.previousPath(),
                    file.changeType().name(),
                    file.fileCategory().name(),
                    file.moduleHint(),
                    file.additions(),
                    file.deletions(),
                    file.changes());
        }
        return changeSet;
    }

    public GitChangeSet findByPipelineRunId(UUID pipelineRunId) {
        ChangeSetRow row = jdbc.query(
                """
                select g.id, g.pipeline_run_id, pr.repository_id, g.base_sha, g.head_sha,
                       pr.pull_request_number, g.provider, g.total_changed_files
                from git_change_sets g
                join pipeline_runs pr on pr.id = g.pipeline_run_id
                where g.pipeline_run_id = ?
                order by g.ingested_at desc
                limit 1
                """,
                rs -> rs.next()
                        ? new ChangeSetRow(
                                rs.getObject("id", UUID.class),
                                rs.getObject("pipeline_run_id", UUID.class),
                                rs.getObject("repository_id", UUID.class),
                                rs.getString("base_sha"),
                                rs.getString("head_sha"),
                                (Long) rs.getObject("pull_request_number"),
                                GitProvider.valueOf(rs.getString("provider")),
                                rs.getInt("total_changed_files"))
                        : null,
                pipelineRunId);
        if (row == null) {
            boolean runExists = Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists(select 1 from pipeline_runs where id=?)",
                    Boolean.class,
                    pipelineRunId));
            if (!runExists) {
                throw new ResourceNotFoundException(
                        "Pipeline run " + pipelineRunId + " was not found.");
            }
            throw new ResourceNotFoundException(
                    "No Git change set has been ingested for pipeline run " + pipelineRunId + ".");
        }
        List<ChangedFile> files = jdbc.query(
                """
                select path,previous_path,change_type,file_category,module_hint,
                       additions,deletions,changes
                from changed_files
                where change_set_id=?
                order by path
                """,
                (rs, index) -> new ChangedFile(
                        rs.getString("path"),
                        rs.getString("previous_path"),
                        ChangeType.valueOf(rs.getString("change_type")),
                        FileCategory.valueOf(rs.getString("file_category")),
                        rs.getString("module_hint"),
                        rs.getInt("additions"),
                        rs.getInt("deletions"),
                        rs.getInt("changes")),
                row.id());
        return new GitChangeSet(
                row.pipelineRunId(),
                row.repositoryId(),
                row.baseSha(),
                row.headSha(),
                row.pullRequestNumber(),
                row.provider(),
                row.totalChangedFiles(),
                files.stream().mapToInt(ChangedFile::additions).sum(),
                files.stream().mapToInt(ChangedFile::deletions).sum(),
                files);
    }

    private record ChangeSetRow(
            UUID id,
            UUID pipelineRunId,
            UUID repositoryId,
            String baseSha,
            String headSha,
            Long pullRequestNumber,
            GitProvider provider,
            int totalChangedFiles) {}
}
