package com.axiom.analysis.classification;

import com.axiom.application.analysis.AnalysisContext;
import com.axiom.domain.evidence.EvidenceSeverity;
import com.axiom.domain.evidence.EvidenceType;
import com.axiom.domain.evidence.FailureEvidence;
import java.math.BigDecimal;
import java.util.List;

public record LogPatternFailureRule(String id, String pattern, EvidenceType evidenceType, EvidenceSeverity severity, String evidenceCode, String description) implements FailureRule {
    @Override public boolean matches(AnalysisContext context) { return context.logs().contains(pattern); }
    @Override public List<FailureEvidence> evaluate(AnalysisContext context) {
        return matches(context) ? List.of(new FailureEvidence(evidenceType, severity, evidenceCode, description, "pipeline-log", new BigDecimal("0.85"))) : List.of();
    }
}
