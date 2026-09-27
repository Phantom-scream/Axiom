package com.axiom.domain.evidence;

import java.math.BigDecimal;
import java.util.Objects;

public record FailureEvidence(EvidenceType type, EvidenceSeverity severity, String code, String description, String source, BigDecimal weight) {
    public FailureEvidence {
        Objects.requireNonNull(type); Objects.requireNonNull(severity); Objects.requireNonNull(code); Objects.requireNonNull(description); Objects.requireNonNull(source); Objects.requireNonNull(weight);
        if (weight.signum() < 0 || weight.compareTo(BigDecimal.ONE) > 0) throw new IllegalArgumentException("weight must be between 0 and 1");
    }
}
