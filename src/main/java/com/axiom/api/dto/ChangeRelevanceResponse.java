package com.axiom.api.dto;

import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.relevance.PersistedChangeRelevance;
import com.axiom.domain.relevance.RelevanceEvidencePriority;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ChangeRelevanceResponse(
        UUID pipelineRunId,
        UUID failureEventId,
        String fingerprint,
        ChangeRelevance relevance,
        double confidence,
        String analyzerVersion,
        String summary,
        Instant createdAt,
        List<EvidenceResponse> evidence,
        List<RelatedFileResponse> relatedFiles) {
    public static ChangeRelevanceResponse from(PersistedChangeRelevance relevance) {
        return new ChangeRelevanceResponse(
                relevance.pipelineRunId(),
                relevance.failureEventId(),
                relevance.fingerprint(),
                relevance.relevance(),
                relevance.confidence(),
                relevance.analyzerVersion(),
                relevance.summary(),
                relevance.createdAt(),
                relevance.evidence().stream().map(EvidenceResponse::from).toList(),
                relevance.relatedFiles().stream().map(RelatedFileResponse::from).toList());
    }

    public record EvidenceResponse(
            String code,
            RelevanceEvidencePriority priority,
            double weight,
            String description,
            String source,
            Map<String, String> metadata) {
        static EvidenceResponse from(
                com.axiom.domain.relevance.ChangeRelevanceEvidence evidence) {
            return new EvidenceResponse(
                    evidence.code(),
                    evidence.priority(),
                    evidence.weight(),
                    evidence.description(),
                    evidence.source(),
                    evidence.metadata());
        }
    }

    public record RelatedFileResponse(String path, String relationship, double weight) {
        static RelatedFileResponse from(
                com.axiom.domain.relevance.RelatedChangedFile relatedFile) {
            return new RelatedFileResponse(
                    relatedFile.path(), relatedFile.relationship(), relatedFile.weight());
        }
    }
}
