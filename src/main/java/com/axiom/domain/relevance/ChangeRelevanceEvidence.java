package com.axiom.domain.relevance;

import java.util.Map;

public record ChangeRelevanceEvidence(
        String code,
        RelevanceEvidencePriority priority,
        double weight,
        String description,
        String source,
        Map<String, String> metadata) {
    public ChangeRelevanceEvidence {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
