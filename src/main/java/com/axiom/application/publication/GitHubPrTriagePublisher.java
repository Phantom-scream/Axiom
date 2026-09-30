package com.axiom.application.publication;

import com.axiom.api.error.AnalysisPrerequisiteException;
import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.application.analysis.PipelineTriageApplicationService;
import com.axiom.domain.publication.GitHubPublication;
import com.axiom.integrations.github.client.GitHubPullRequestClient;
import com.axiom.observability.AxiomMetrics;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class GitHubPrTriagePublisher {
    private final JdbcTemplate jdbc;
    private final PipelineTriageApplicationService triage;
    private final GitHubPrCommentRenderer renderer;
    private final GitHubPullRequestClient pullRequests;
    private final GitHubPublicationPersistenceService publications;
    private final AxiomMetrics metrics;

    public GitHubPrTriagePublisher(
            JdbcTemplate jdbc,
            PipelineTriageApplicationService triage,
            GitHubPrCommentRenderer renderer,
            GitHubPullRequestClient pullRequests,
            GitHubPublicationPersistenceService publications,
            AxiomMetrics metrics) {
        this.jdbc = jdbc;
        this.triage = triage;
        this.renderer = renderer;
        this.pullRequests = pullRequests;
        this.publications = publications;
        this.metrics = metrics;
    }

    public PublishResult publish(UUID pipelineRunId) {
        var persistedTriage = triage.get(pipelineRunId);
        PipelineTarget target = target(pipelineRunId);
        String body = renderer.render(persistedTriage);
        var existing = publications.findPrComment(pipelineRunId).or(() ->
                publications.findPrCommentForPullRequest(
                        target.repositoryId(), target.pullRequestNumber()));
        var response = existing.isPresent()
                ? pullRequests.updateComment(
                        target.owner(),
                        target.repository(),
                        parseExternalId(existing.get()),
                        body)
                : pullRequests.createComment(
                        target.owner(), target.repository(), target.pullRequestNumber(), body);
        GitHubPublication publication = publications.savePrComment(
                pipelineRunId,
                Long.toString(response.id()),
                response.htmlUrl(),
                persistedTriage.triageVersion());
        metrics.publication("pr_comment", existing.isPresent() ? "updated" : "created");
        return new PublishResult(
                pipelineRunId,
                target.pullRequestNumber(),
                publication.externalId(),
                publication.externalUrl(),
                existing.isPresent() ? "UPDATED" : "CREATED",
                publication.triageVersion());
    }

    private PipelineTarget target(UUID pipelineRunId) {
        PipelineTarget target = jdbc.query(
                """
                select r.id,r.owner,r.name,pr.pull_request_number
                from pipeline_runs pr join repositories r on r.id=pr.repository_id
                where pr.id=?
                """,
                rs -> rs.next()
                        ? new PipelineTarget(
                                rs.getObject(1, UUID.class),
                                rs.getString(2),
                                rs.getString(3),
                                (Long) rs.getObject(4))
                        : null,
                pipelineRunId);
        if (target == null) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
        if (target.pullRequestNumber() == null) {
            throw new AnalysisPrerequisiteException(
                    "Pipeline run " + pipelineRunId + " is not associated with a pull request.");
        }
        return target;
    }

    private long parseExternalId(GitHubPublication publication) {
        try {
            return Long.parseLong(publication.externalId());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Stored GitHub PR comment identifier is invalid.", exception);
        }
    }

    private record PipelineTarget(
            UUID repositoryId, String owner, String repository, Long pullRequestNumber) {}

    public record PublishResult(
            UUID pipelineRunId,
            long pullRequestNumber,
            String externalCommentId,
            String externalUrl,
            String operation,
            String triageVersion) {}
}
