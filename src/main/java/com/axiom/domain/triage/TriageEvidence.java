package com.axiom.domain.triage;

import java.util.Map;
import java.util.UUID;

public record TriageEvidence(
        UUID failureEventId,
        String code,
        TriageEvidencePriority priority,
        double weight,
        String description,
        Map<String, String> metadata) {
    public TriageEvidence {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
