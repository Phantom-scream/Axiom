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

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GitHubPublicationPersistenceService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<GitHubPublication> findCheck(UUID pipelineRunId) {
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
                GITHUB_CHECK);
    }

    @Transactional
    public GitHubPublication saveCheck(
            UUID pipelineRunId, String externalId, String externalUrl, String triageVersion) {
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
                GITHUB_CHECK,
                externalId,
                externalUrl,
                triageVersion,
                now,
                now);
        return findCheck(pipelineRunId).orElseThrow();
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
