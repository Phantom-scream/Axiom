package com.axiom.application.pipeline;

import com.axiom.api.error.GitComparisonUnavailableException;
import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.GitChangeReference;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GitChangeReferenceResolver {
    private final JdbcTemplate jdbc;

    public GitChangeReferenceResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public GitChangeReference resolve(UUID pipelineRunId) {
        PipelineMetadata metadata = jdbc.query(
                """
                select pr.id, pr.repository_id, r.provider, r.owner, r.name,
                       pr.base_sha, pr.commit_sha, pr.pull_request_number, pr.event_name
                from pipeline_runs pr
                join repositories r on r.id = pr.repository_id
                where pr.id = ?
                """,
                rs -> rs.next()
                        ? new PipelineMetadata(
                                rs.getObject("id", UUID.class),
                                rs.getObject("repository_id", UUID.class),
                                CiProviderType.valueOf(rs.getString("provider")),
                                rs.getString("owner"),
                                rs.getString("name"),
                                rs.getString("base_sha"),
                                rs.getString("commit_sha"),
                                (Long) rs.getObject("pull_request_number"),
                                rs.getString("event_name"))
                        : null,
                pipelineRunId);
        if (metadata == null) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
        return resolve(metadata);
    }

    GitChangeReference resolve(PipelineMetadata metadata) {
        if (metadata.baseSha() == null || metadata.baseSha().isBlank()) {
            throw new GitComparisonUnavailableException(
                    "Pipeline run " + metadata.pipelineRunId()
                            + " has no reliable base SHA. Re-ingest a run whose provider metadata includes a comparison base.");
        }
        if (metadata.headSha() == null || metadata.headSha().isBlank()) {
            throw new GitComparisonUnavailableException(
                    "Pipeline run " + metadata.pipelineRunId() + " has no reliable head SHA.");
        }
        return new GitChangeReference(
                metadata.pipelineRunId(),
                metadata.repositoryId(),
                metadata.provider(),
                metadata.owner(),
                metadata.repository(),
                metadata.baseSha(),
                metadata.headSha(),
                metadata.pullRequestNumber(),
                metadata.eventName());
    }

    record PipelineMetadata(
            UUID pipelineRunId,
            UUID repositoryId,
            CiProviderType provider,
            String owner,
            String repository,
            String baseSha,
            String headSha,
            Long pullRequestNumber,
            String eventName) {}
}
