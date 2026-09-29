package com.axiom.application.publication;

import com.axiom.domain.publication.GitHubPublication;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubPublicationPersistenceService {
    public static final String GITHUB_CHECK = "GITHUB_CHECK";
    public static final String PR_COMMENT = "PR_COMMENT";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GitHubPublicationPersistenceService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<GitHubPublication> findCheck(UUID pipelineRunId) {
        return find(pipelineRunId, GITHUB_CHECK);
    }

    public Optional<GitHubPublication> findPrComment(UUID pipelineRunId) {
        return find(pipelineRunId, PR_COMMENT);
    }

    public Optional<GitHubPublication> findPrCommentForPullRequest(
            UUID repositoryId, long pullRequestNumber) {
        return jdbc.query(
                """
                select p.id,p.pipeline_run_id,p.publication_type,p.external_id,p.external_url,
                       p.triage_version,p.published_at,p.updated_at
                from github_publications p
                join pipeline_runs pr on pr.id=p.pipeline_run_id
                where pr.repository_id=? and pr.pull_request_number=?
                  and p.publication_type=?
                order by coalesce(p.updated_at,p.published_at) desc,p.id
                limit 1
                """,
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(),
                repositoryId,
                pullRequestNumber,
                PR_COMMENT);
    }

    private Optional<GitHubPublication> find(UUID pipelineRunId, String publicationType) {
        return jdbc.query(
                """
                select id,pipeline_run_id,publication_type,external_id,external_url,
                       triage_version,published_at,updated_at
                from github_publications
                where pipeline_run_id=? and publication_type=?
                """,
                rs -> rs.next()
                        ? Optional.of(map(rs))
                        : Optional.empty(),
                pipelineRunId,
                publicationType);
    }

    @Transactional
    public GitHubPublication saveCheck(
            UUID pipelineRunId, String externalId, String externalUrl, String triageVersion) {
        return save(pipelineRunId, GITHUB_CHECK, externalId, externalUrl, triageVersion);
    }

    @Transactional
    public GitHubPublication savePrComment(
            UUID pipelineRunId, String externalId, String externalUrl, String triageVersion) {
        return save(pipelineRunId, PR_COMMENT, externalId, externalUrl, triageVersion);
    }

    private GitHubPublication save(
            UUID pipelineRunId,
            String publicationType,
            String externalId,
            String externalUrl,
            String triageVersion) {
        var now = Timestamp.from(clock.instant());
        jdbc.update(
                """
                insert into github_publications(
                    id,pipeline_run_id,publication_type,external_id,external_url,
                    triage_version,published_at,updated_at)
                values(?,?,?,?,?,?,?,?)
                on conflict(pipeline_run_id,publication_type) do update set
                    external_id=excluded.external_id,
                    external_url=excluded.external_url,
                    triage_version=excluded.triage_version,
                    updated_at=excluded.updated_at
                """,
                UUID.randomUUID(),
                pipelineRunId,
                publicationType,
                externalId,
                externalUrl,
                triageVersion,
                now,
                now);
        return find(pipelineRunId, publicationType).orElseThrow();
    }

    private GitHubPublication map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new GitHubPublication(
                rs.getObject("id", UUID.class),
                rs.getObject("pipeline_run_id", UUID.class),
                rs.getString("publication_type"),
                rs.getString("external_id"),
                rs.getString("external_url"),
                rs.getString("triage_version"),
                rs.getTimestamp("published_at").toInstant(),
                updated == null ? null : updated.toInstant());
    }
}
