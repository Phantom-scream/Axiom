package com.axiom.application.publication;

import com.axiom.domain.triage.PipelineTriageResult;
import com.axiom.integrations.github.client.GitHubChecksClient;
import org.springframework.stereotype.Component;

@Component
public class GitHubCheckRenderer {
    private static final int SUMMARY_LIMIT = 8_000;
    private static final int TEXT_LIMIT = 60_000;

    public GitHubChecksClient.CheckOutput render(PipelineTriageResult triage) {
        String title = title(triage);
        StringBuilder summary = new StringBuilder();
        append(summary, "**Primary failure:** ", triage.primaryClassification() == null
                ? "No supported primary failure"
                : human(triage.primaryClassification().name()));
        if (triage.primaryFingerprint() != null) {
            append(summary, "\n\n**Fingerprint:** `", safe(triage.primaryFingerprint()) + "`");
        }
        if (triage.changeRelevance() != null) {
            append(summary, "\n\n**Change relevance:** ", human(triage.changeRelevance().name()));
        }
        append(summary, "\n\n**Rerun guidance:** ", human(triage.rerunRecommendation().name()));
        append(summary, "\n\n", safe(triage.summary()));

        if (!triage.evidence().isEmpty()) {
            summary.append("\n\n**Top evidence**\n");
            triage.evidence().stream()
                    .sorted(java.util.Comparator.comparingInt(
                            evidence -> evidencePriority(evidence.priority())))
                    .limit(3)
                    .forEach(evidence -> summary
                            .append("- ")
                            .append(safe(evidence.description()))
                            .append('\n'));
        }
        if (!triage.actions().isEmpty()) {
            summary.append("\n**Recommended actions**\n");
            triage.actions().stream().limit(3).forEach(action -> summary
                    .append(action.priority())
                    .append(". ")
                    .append(safe(action.description()))
                    .append(" — ")
                    .append(safe(action.reason()))
                    .append('\n'));
        }

        StringBuilder text = new StringBuilder("### Ranked failure details\n\n");
        if (triage.failures().isEmpty()) {
            text.append("No extracted failure event had enough evidence for ranking.\n");
        } else {
            triage.failures().stream().limit(20).forEach(failure -> text
                    .append("- **")
                    .append(human(failure.role().name()))
                    .append("** — ")
                    .append(human(failure.classification().name()))
                    .append("; fingerprint `")
                    .append(safe(failure.fingerprint()))
                    .append("`; historical occurrences ")
                    .append(failure.historicalOccurrenceCount())
                    .append("; stability ")
                    .append(failure.testStability() == null
                            ? "not available"
                            : human(failure.testStability().name()))
                    .append("; unchanged-rerun pass ")
                    .append(failure.unchangedRerunPassed() ? "observed" : "not observed")
                    .append('\n'));
        }
        text.append("\nAxiom reports deterministic evidence and investigation priority; it does not prove causation or alter the source CI result.");
        return new GitHubChecksClient.CheckOutput(
                bounded(title, 255), bounded(summary.toString(), SUMMARY_LIMIT), bounded(text.toString(), TEXT_LIMIT));
    }

    private String title(PipelineTriageResult triage) {
        if (triage.primaryClassification() != null) {
            return "Axiom: " + human(triage.primaryClassification().name()) + " detected";
        }
        if ("NO_TRIAGE_NEEDED".equals(triage.status())) return "Axiom: no failure triage needed";
        return "Axiom: insufficient evidence";
    }

    private void append(StringBuilder value, String prefix, String content) {
        value.append(prefix).append(content);
    }

    private String human(String value) {
        return safe(value.toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    private String safe(String value) {
        if (value == null) return "not available";
        return value.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("|", "\\|")
                .replace("#", "\\#");
    }

    private String bounded(String value, int limit) {
        if (value.length() <= limit) return value;
        return value.substring(0, limit - 20) + "\n\n_Output truncated._";
    }

    private int evidencePriority(
            com.axiom.domain.triage.TriageEvidencePriority priority) {
        return switch (priority) {
            case ROOT -> 0;
            case STRONG -> 1;
            case MEDIUM -> 2;
            case WEAK -> 3;
            case COUNTER -> 4;
        };
    }
}
