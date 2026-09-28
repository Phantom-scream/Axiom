package com.axiom.domain.relevance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PersistedChangeRelevance(
        UUID id,
        UUID pipelineRunId,
        UUID failureEventId,
        String fingerprint,
        ChangeRelevance relevance,
        double confidence,
        String analyzerVersion,
        String summary,
        Instant createdAt,
        List<ChangeRelevanceEvidence> evidence,
        List<RelatedChangedFile> relatedFiles) {
    public PersistedChangeRelevance {
        evidence = List.copyOf(evidence);
        relatedFiles = List.copyOf(relatedFiles);
    }
}
