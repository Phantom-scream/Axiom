package com.axiom.domain.diagnosis;

import com.axiom.domain.evidence.FailureEvidence;
import com.axiom.domain.failure.FailureClassification;
import java.math.BigDecimal;
import java.util.List;

public record Diagnosis(FailureClassification classification, BigDecimal confidence, String summary, String probableCause, String recommendedAction, List<FailureEvidence> evidence) {
    public Diagnosis { evidence = List.copyOf(evidence); }
}
