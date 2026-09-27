package com.axiom.analysis.classification;

import com.axiom.application.analysis.AnalysisContext;
import com.axiom.domain.evidence.FailureEvidence;
import java.util.List;

public interface FailureRule {
    String id();
    boolean matches(AnalysisContext context);
    List<FailureEvidence> evaluate(AnalysisContext context);
}
