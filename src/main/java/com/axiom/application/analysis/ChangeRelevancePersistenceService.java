package com.axiom.application.analysis;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.relevance.ChangeRelevanceEvidence;
import com.axiom.domain.relevance.PersistedChangeRelevance;
import com.axiom.domain.relevance.RelatedChangedFile;
import com.axiom.domain.relevance.RelevanceEvidencePriority;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChangeRelevancePersistenceService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ChangeRelevancePersistenceService(
            JdbcTemplate jdbc, Clock clock, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PersistedChangeRelevance save(
            UUID pipelineRunId,
            UUID failureEventId,
            String fingerprint,
            com.axiom.analysis.correlation.ChangeRelevanceService.DetailedResult result) {
        UUID resultId = jdbc.query(
                """
                insert into change_relevance_results(
                    id,pipeline_run_id,failure_event_id,fingerprint,relevance,confidence,
                    analyzer_version,summary,created_at)
                values(?,?,?,?,?,?,?,?,?)
                on conflict(failure_event_id,analyzer_version) do update set
                    pipeline_run_id=excluded.pipeline_run_id,
                    fingerprint=excluded.fingerprint,
                    relevance=excluded.relevance,
                    confidence=excluded.confidence,
                    summary=excluded.summary,
                    created_at=excluded.created_at
                returning id
                """,
                rs -> {
                    rs.next();
                    return rs.getObject(1, UUID.class);
                },
                UUID.randomUUID(),
                pipelineRunId,
                failureEventId,
                fingerprint,
                result.relevance().name(),
                result.confidence(),
                result.version(),
                result.summary(),
                Timestamp.from(clock.instant()));

        jdbc.update("delete from change_relevance_evidence where relevance_result_id=?", resultId);
        jdbc.update("delete from relevance_related_files where relevance_result_id=?", resultId);
        for (ChangeRelevanceEvidence evidence : result.evidence()) {
            jdbc.update(
                    """
                    insert into change_relevance_evidence(
                        id,relevance_result_id,code,priority,weight,description,source,metadata)
                    values(?,?,?,?,?,?,?,cast(? as jsonb))
                    """,
                    UUID.randomUUID(),
                    resultId,
                    evidence.code(),
                    evidence.priority().name(),
                    evidence.weight(),
                    evidence.description(),
                    evidence.source(),
                    writeMetadata(evidence.metadata()));
        }
        for (RelatedChangedFile file : result.relatedFiles()) {
            jdbc.update(
                    """
                    insert into relevance_related_files(
                        relevance_result_id,changed_file_id,relationship,weight)
                    values(?,?,?,?)
                    """,
                    resultId,
                    file.changedFileId(),
                    file.relationship(),
                    file.weight());
        }
        return findById(resultId);
    }

    public List<PersistedChangeRelevance> findByPipelineRunId(UUID pipelineRunId) {
        ensureRunExists(pipelineRunId);
        List<ResultRow> rows = resultRows(
                "where pipeline_run_id=? order by created_at, failure_event_id", pipelineRunId);
        return hydrate(rows, pipelineRunId);
    }

    public PersistedChangeRelevance findByPipelineRunIdAndFingerprint(
            UUID pipelineRunId, String fingerprint) {
        ensureRunExists(pipelineRunId);
        List<ResultRow> rows = resultRows(
                "where pipeline_run_id=? and fingerprint=? order by created_at desc, failure_event_id limit 1",
                pipelineRunId,
                fingerprint);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No persisted change relevance exists for fingerprint " + fingerprint + ".");
        }
        return hydrate(rows, pipelineRunId).getFirst();
    }

    private PersistedChangeRelevance findById(UUID id) {
        ResultRow row = resultRows("where id=?", id).getFirst();
        return hydrate(List.of(row), row.pipelineRunId()).getFirst();
    }

    private List<ResultRow> resultRows(String whereClause, Object... arguments) {
        return jdbc.query(
                """
                select id,pipeline_run_id,failure_event_id,fingerprint,relevance,confidence,
                       analyzer_version,summary,created_at
                from change_relevance_results
                """ + whereClause,
                (rs, row) -> new ResultRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("pipeline_run_id", UUID.class),
                        rs.getObject("failure_event_id", UUID.class),
                        rs.getString("fingerprint"),
                        ChangeRelevance.valueOf(rs.getString("relevance")),
                        rs.getDouble("confidence"),
                        rs.getString("analyzer_version"),
                        rs.getString("summary"),
                        rs.getTimestamp("created_at").toInstant()),
                arguments);
    }

    private List<PersistedChangeRelevance> hydrate(List<ResultRow> rows, UUID pipelineRunId) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, List<ChangeRelevanceEvidence>> evidence = new HashMap<>();
        List<EvidenceRow> evidenceRows = jdbc.query(
                """
                select e.relevance_result_id,e.code,e.priority,e.weight,e.description,e.source,
                       e.metadata::text metadata
                from change_relevance_evidence e
                join change_relevance_results r on r.id=e.relevance_result_id
                where r.pipeline_run_id=? order by e.code,e.id
                """,
                (rs, row) -> new EvidenceRow(
                        rs.getObject("relevance_result_id", UUID.class),
                        new ChangeRelevanceEvidence(
                                rs.getString("code"),
                                RelevanceEvidencePriority.valueOf(rs.getString("priority")),
                                rs.getDouble("weight"),
                                rs.getString("description"),
                                rs.getString("source"),
                                readMetadata(rs.getString("metadata")))),
                pipelineRunId);
        evidenceRows.forEach(row -> evidence.computeIfAbsent(
                        row.relevanceResultId(), ignored -> new ArrayList<>())
                .add(row.evidence()));
        Map<UUID, List<RelatedChangedFile>> related = new HashMap<>();
        List<RelatedFileRow> relatedRows = jdbc.query(
                """
                select rf.relevance_result_id,rf.changed_file_id,cf.path,rf.relationship,rf.weight
                from relevance_related_files rf
                join changed_files cf on cf.id=rf.changed_file_id
                join change_relevance_results r on r.id=rf.relevance_result_id
                where r.pipeline_run_id=? order by cf.path,rf.relationship
                """,
                (rs, row) -> new RelatedFileRow(
                        rs.getObject("relevance_result_id", UUID.class),
                        new RelatedChangedFile(
                                rs.getObject("changed_file_id", UUID.class),
                                rs.getString("path"),
                                rs.getString("relationship"),
                                rs.getDouble("weight"))),
                pipelineRunId);
        relatedRows.forEach(row -> related.computeIfAbsent(
                        row.relevanceResultId(), ignored -> new ArrayList<>())
                .add(row.relatedFile()));
        return rows.stream()
                .map(row -> new PersistedChangeRelevance(
                        row.id(),
                        row.pipelineRunId(),
                        row.failureEventId(),
                        row.fingerprint(),
                        row.relevance(),
                        row.confidence(),
                        row.analyzerVersion(),
                        row.summary(),
                        row.createdAt(),
                        evidence.getOrDefault(row.id(), List.of()),
                        related.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private void ensureRunExists(UUID pipelineRunId) {
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from pipeline_runs where id=?)",
                Boolean.class,
                pipelineRunId));
        if (!exists) {
            throw new ResourceNotFoundException("Pipeline run " + pipelineRunId + " was not found.");
        }
    }

    private String writeMetadata(Map<String, String> metadata) {
        return objectMapper.writeValueAsString(metadata);
    }

    private Map<String, String> readMetadata(String metadata) {
        if (metadata == null) return Map.of();
        return objectMapper.readValue(metadata, new TypeReference<>() {});
    }

    private record ResultRow(
            UUID id,
            UUID pipelineRunId,
            UUID failureEventId,
            String fingerprint,
            ChangeRelevance relevance,
            double confidence,
            String analyzerVersion,
            String summary,
            java.time.Instant createdAt) {}

    private record EvidenceRow(
            UUID relevanceResultId, ChangeRelevanceEvidence evidence) {}

    private record RelatedFileRow(UUID relevanceResultId, RelatedChangedFile relatedFile) {}
}
