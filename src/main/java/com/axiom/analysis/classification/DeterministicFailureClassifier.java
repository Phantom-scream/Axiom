package com.axiom.analysis.classification;

import com.axiom.application.analysis.AnalysisContext;
import com.axiom.domain.diagnosis.Diagnosis;
import com.axiom.domain.evidence.EvidenceSeverity;
import com.axiom.domain.evidence.EvidenceType;
import com.axiom.domain.evidence.FailureEvidence;
import com.axiom.domain.failure.FailureClassification;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** Bootstrap-only, explainable examples. This is deliberately not a production rules engine. */
@Component
public class DeterministicFailureClassifier implements FailureClassifier {
    private final List<FailureRule> rules = List.of(
            new LogPatternFailureRule("database-connection-refused", "Connection refused on PostgreSQL port 5432", EvidenceType.INFRASTRUCTURE_SIGNAL, EvidenceSeverity.HIGH, "DATABASE_CONNECTION_REFUSED", "The pipeline could not connect to PostgreSQL."),
            new LogPatternFailureRule("dependency-resolution", "dependency could not be resolved", EvidenceType.DEPENDENCY_SIGNAL, EvidenceSeverity.HIGH, "DEPENDENCY_RESOLUTION_FAILED", "A required dependency could not be resolved."),
            new LogPatternFailureRule("test-assertion", "AssertionError", EvidenceType.TEST_RESULT, EvidenceSeverity.MEDIUM, "TEST_ASSERTION_FAILED", "A test assertion failed."));

    @Override public Diagnosis classify(AnalysisContext context) {
        List<FailureEvidence> evidence = rules.stream().filter(rule -> rule.matches(context)).flatMap(rule -> rule.evaluate(context).stream()).toList();
        if (evidence.isEmpty()) return unknown();
        FailureEvidence primary = evidence.getFirst();
        FailureClassification classification = switch (primary.code()) {
            case "DATABASE_CONNECTION_REFUSED" -> FailureClassification.INFRASTRUCTURE_FAILURE;
            case "DEPENDENCY_RESOLUTION_FAILED" -> FailureClassification.DEPENDENCY_FAILURE;
            case "TEST_ASSERTION_FAILED" -> FailureClassification.TEST_FAILURE;
            default -> FailureClassification.UNKNOWN;
        };
        return new Diagnosis(classification, new BigDecimal("0.85"), primary.description(), primary.description(), "Inspect the cited pipeline evidence and rerun after correcting the underlying condition.", evidence);
    }
    private Diagnosis unknown() {
        FailureEvidence evidence = new FailureEvidence(EvidenceType.LOG_PATTERN, EvidenceSeverity.INFO, "NO_DEMO_RULE_MATCHED", "No bootstrap demonstration rule matched the supplied log.", "demo-classifier", new BigDecimal("0.10"));
        return new Diagnosis(FailureClassification.UNKNOWN, new BigDecimal("0.10"), "No deterministic demo classification is available.", "The supplied evidence did not match a bootstrap rule.", "Collect additional logs and metadata before diagnosing.", List.of(evidence));
    }
}
