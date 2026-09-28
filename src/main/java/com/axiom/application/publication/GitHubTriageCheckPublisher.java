package com.axiom.application.publication;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.application.analysis.PipelineTriageApplicationService;
import com.axiom.domain.publication.GitHubPublication;
import com.axiom.integrations.github.client.GitHubChecksClient;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GitHubTriageCheckPublisher {
    private final JdbcTemplate jdbc;
    private final PipelineTriageApplicationService triage;
    private final GitHubCheckRenderer renderer;
    private final GitHubChecksClient checks;
    private final GitHubPublicationPersistenceService publications;

    public GitHubTriageCheckPublisher(
            JdbcTemplate jdbc,
            PipelineTriageApplicationService triage,
            GitHubCheckRenderer renderer,
            GitHubChecksClient checks,
            GitHubPublicationPersistenceService publications) {
        this.jdbc = jdbc;
        this.triage = triage;
        this.renderer = renderer;
        this.checks = checks;
        this.publications = publications;
    }

    public PublishResult publish(UUID pipelineRunId) {
        var persistedTriage = triage.get(pipelineRunId);
        PipelineTarget target = target(pipelineRunId);
        var output = renderer.render(persistedTriage);
        var existing = publications.findCheck(pipelineRunId);
        var response = existing.isPresent()
                ? checks.update(
                        target.owner(),
                        target.repository(),
                        parseExternalId(existing.get()),
                        output)
                : checks.create(target.owner(), target.repository(), target.headSha(), output);
        GitHubPublication publication = publications.saveCheck(
                pipelineRunId,
                Long.toString(response.id()),
                response.htmlUrl(),
                persistedTriage.triageVersion());
        return new PublishResult(
                pipelineRunId,
                publication.externalId(),
                publication.externalUrl(),
                existing.isPresent() ? "UPDATED" : "CREATED",
                publication.triageVersion());
    }

    private PipelineTarget target(UUID pipelineRunId) {
        PipelineTarget target = jdbc.query(
                """
                select r.owner,r.name,pr.commit_sha
                from pipeline_runs pr join repositories r on r.id=pr.repository_id
                where pr.id=?
                """,
                rs -> rs.next()
                        ? new PipelineTarget(rs.getString(1), rs.getString(2), rs.getString(3))
                        : null,
                pipelineRunId);
        if (target == null) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
        if (target.headSha() == null || target.headSha().isBlank()) {
            throw new IllegalArgumentException(
                    "Pipeline run " + pipelineRunId + " has no head commit SHA for GitHub Checks.");
        }
        return target;
    }

    private long parseExternalId(GitHubPublication publication) {
        try {
            return Long.parseLong(publication.externalId());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Stored GitHub Check identifier is invalid.", exception);
        }
    }

    private record PipelineTarget(String owner, String repository, String headSha) {}

    public record PublishResult(
            UUID pipelineRunId,
            String externalCheckRunId,
            String externalUrl,
            String operation,
            String triageVersion) {}
}
