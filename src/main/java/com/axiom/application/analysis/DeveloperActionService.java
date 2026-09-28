package com.axiom.application.analysis;

import com.axiom.config.TriageProperties;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.triage.ActionType;
import com.axiom.domain.triage.RecommendedAction;
import com.axiom.domain.triage.RerunRecommendation;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DeveloperActionService {
    private final TriageProperties properties;

    public DeveloperActionService(TriageProperties properties) {
        this.properties = properties;
    }

    public List<RecommendedAction> generate(
            PipelineTriageService.Result triage, boolean hasRelatedSourceFiles) {
        if (triage.primaryClassification() == null) return List.of();
        List<ActionDraft> drafts = new ArrayList<>();
        RerunRecommendation rerun = RerunRecommendation.valueOf(triage.rerunRecommendation());
        if (rerun == RerunRecommendation.RECOMMENDED) {
            drafts.add(new ActionDraft(
                    ActionType.RERUN_PIPELINE,
                    "Rerun the pipeline once.",
                    "Historical or operational evidence indicates a rerun may produce a different result."));
        }
        drafts.add(classificationAction(triage.primaryClassification()));
        ChangeRelevance relevance = triage.failureRankings().stream()
                .filter(entry -> entry.failureEventId().equals(triage.primaryFailureEventId()))
                .map(entry -> entry.changeRelevance())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (hasRelatedSourceFiles
                && (relevance == ChangeRelevance.RELATED
                        || relevance == ChangeRelevance.LIKELY_RELATED)) {
            drafts.add(new ActionDraft(
                    ActionType.INSPECT_SOURCE_CHANGE,
                    "Inspect the source files related to the primary failure.",
                    "Persisted change relevance links source changes to the failure evidence."));
        }
        if (drafts.isEmpty()) {
            drafts.add(new ActionDraft(
                    ActionType.REVIEW_FAILURE_DETAILS,
                    "Review the highest-ranked failure details.",
                    "The available evidence does not support a more specific action."));
        }
        List<RecommendedAction> actions = new ArrayList<>();
        int limit = Math.min(properties.effectiveMaxActions(), drafts.size());
        for (int index = 0; index < limit; index++) {
            ActionDraft draft = drafts.get(index);
            actions.add(new RecommendedAction(
                    index + 1, draft.type(), draft.description(), draft.reason()));
        }
        return List.copyOf(actions);
    }

    private ActionDraft classificationAction(FailureClassification classification) {
        return switch (classification) {
            case INFRASTRUCTURE_FAILURE -> new ActionDraft(
                    ActionType.INSPECT_INFRASTRUCTURE,
                    "Inspect the affected CI service or runner if the failure repeats.",
                    "The primary signal is classified as an infrastructure failure.");
            case DEPENDENCY_FAILURE -> new ActionDraft(
                    ActionType.INSPECT_DEPENDENCY_CONFIGURATION,
                    "Inspect dependency manifests, lockfiles, and artifact resolution details.",
                    "The primary signal is classified as a dependency failure.");
            case RESOURCE_EXHAUSTION -> new ActionDraft(
                    ActionType.INSPECT_RESOURCES,
                    "Inspect runner memory, disk, and process resource limits.",
                    "The primary signal indicates resource exhaustion.");
            case CONFIGURATION_FAILURE, ENVIRONMENT_FAILURE -> new ActionDraft(
                    ActionType.INSPECT_CONFIGURATION,
                    "Inspect the relevant application and CI configuration.",
                    "The primary signal is configuration or environment related.");
            case TEST_FAILURE, FLAKY_TEST -> new ActionDraft(
                    ActionType.INSPECT_TEST,
                    "Inspect the correlated test failure and its structured history.",
                    "The primary signal is test related.");
            case EXTERNAL_SERVICE_FAILURE -> new ActionDraft(
                    ActionType.INSPECT_EXTERNAL_SERVICE,
                    "Inspect the external service status and integration boundary.",
                    "The primary signal indicates an external-service failure.");
            case BUILD_FAILURE, PRODUCT_REGRESSION -> new ActionDraft(
                    ActionType.INSPECT_BUILD,
                    "Inspect the specific build failure before rerunning.",
                    "The primary signal is build related.");
            case TIMEOUT, UNKNOWN -> new ActionDraft(
                    ActionType.REVIEW_FAILURE_DETAILS,
                    "Review the highest-ranked failure details.",
                    "The available classification does not support a more specific action.");
        };
    }

    private record ActionDraft(ActionType type, String description, String reason) {}
}
