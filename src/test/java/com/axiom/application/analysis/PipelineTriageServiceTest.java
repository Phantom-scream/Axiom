package com.axiom.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.config.TriageProperties;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.triage.ActionType;
import com.axiom.domain.triage.FailureRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineTriageServiceTest {
    private final TriageProperties properties =
            new TriageProperties("triage-v1", 3, .6, .1);
    private final PipelineTriageService service = new PipelineTriageService(properties);
    private final DeveloperActionService actions = new DeveloperActionService(properties);

    @Test
    void resourceExhaustionOutranksGenericExitAndMarksItDownstream() {
        var result = service.triage(List.of(
                failure("exit", FailureClassification.BUILD_FAILURE, true, null, false, false, null),
                failure(
                        "oom",
                        FailureClassification.RESOURCE_EXHAUSTION,
                        false,
                        null,
                        false,
                        false,
                        null)));

        assertThat(result.primaryFingerprint()).isEqualTo("oom");
        assertThat(result.rerunRecommendation()).isEqualTo("CONSIDER");
        assertThat(result.failureRankings())
                .filteredOn(entry -> entry.fingerprint().equals("exit"))
                .singleElement()
                .extracting("role")
                .isEqualTo(FailureRole.DOWNSTREAM);
    }

    @Test
    void dependencyAndInfrastructureSignalsOutrankGenericOrDownstreamFailures() {
        var dependency = service.triage(List.of(
                failure(
                        "generic",
                        FailureClassification.BUILD_FAILURE,
                        true,
                        null,
                        false,
                        false,
                        null),
                failure(
                        "dependency",
                        FailureClassification.DEPENDENCY_FAILURE,
                        false,
                        ChangeRelevance.RELATED,
                        false,
                        false,
                        null)));
        var infrastructure = service.triage(List.of(
                failure(
                        "test",
                        FailureClassification.TEST_FAILURE,
                        false,
                        null,
                        false,
                        false,
                        null),
                failure(
                        "infra",
                        FailureClassification.INFRASTRUCTURE_FAILURE,
                        false,
                        ChangeRelevance.UNRELATED,
                        false,
                        false,
                        null)));

        assertThat(dependency.primaryFingerprint()).isEqualTo("dependency");
        assertThat(dependency.rerunRecommendation()).isEqualTo("NOT_RECOMMENDED");
        assertThat(infrastructure.primaryFingerprint()).isEqualTo("infra");
        assertThat(infrastructure.failureRankings())
                .filteredOn(entry -> entry.fingerprint().equals("test"))
                .singleElement()
                .extracting("role")
                .isEqualTo(FailureRole.DOWNSTREAM);
    }

    @Test
    void equallySpecificIndependentFailuresMayLeavePrimaryUnknown() {
        var result = service.triage(List.of(
                failure(
                        "infra",
                        FailureClassification.INFRASTRUCTURE_FAILURE,
                        false,
                        null,
                        false,
                        false,
                        null),
                failure(
                        "dependency",
                        FailureClassification.DEPENDENCY_FAILURE,
                        false,
                        null,
                        false,
                        false,
                        null)));

        assertThat(result.primaryFingerprint()).isNull();
        assertThat(result.primaryRole()).isEqualTo("UNKNOWN");
        assertThat(result.rerunRecommendation()).isEqualTo("INSUFFICIENT_EVIDENCE");
    }

    @Test
    void rerunRecommendationsUsePositiveNegativeAndMixedEvidence() {
        var unchangedPass = service.triage(List.of(failure(
                "timeout", FailureClassification.TIMEOUT, false, null, true, false, null)));
        var infrastructure = service.triage(List.of(failure(
                "infra",
                FailureClassification.INFRASTRUCTURE_FAILURE,
                false,
                ChangeRelevance.UNRELATED,
                false,
                false,
                null)));
        var consistent = service.triage(List.of(failure(
                "test",
                FailureClassification.TEST_FAILURE,
                false,
                null,
                false,
                false,
                TestStabilityClass.CONSISTENTLY_FAILING)));
        var mixed = service.triage(List.of(failure(
                "build",
                FailureClassification.BUILD_FAILURE,
                false,
                ChangeRelevance.RELATED,
                true,
                false,
                null)));
        var insufficient = service.triage(List.of(failure(
                "test", FailureClassification.TEST_FAILURE, false, null, false, false, null)));

        assertThat(unchangedPass.rerunRecommendation()).isEqualTo("RECOMMENDED");
        assertThat(infrastructure.rerunRecommendation()).isEqualTo("RECOMMENDED");
        assertThat(consistent.rerunRecommendation()).isEqualTo("NOT_RECOMMENDED");
        assertThat(mixed.rerunRecommendation()).isEqualTo("CONSIDER");
        assertThat(insufficient.rerunRecommendation()).isEqualTo("INSUFFICIENT_EVIDENCE");
    }

    @Test
    void actionsAreEvidenceBackedOrderedAndBounded() {
        var result = service.triage(List.of(failure(
                "infra",
                FailureClassification.INFRASTRUCTURE_FAILURE,
                false,
                ChangeRelevance.UNRELATED,
                true,
                false,
                null)));
        var recommended = actions.generate(result, true);
        var dependency = service.triage(List.of(failure(
                "dependency",
                FailureClassification.DEPENDENCY_FAILURE,
                false,
                ChangeRelevance.RELATED,
                false,
                false,
                null)));
        var dependencyActions = actions.generate(dependency, true);

        assertThat(recommended).hasSizeLessThanOrEqualTo(3);
        assertThat(recommended.getFirst().type()).isEqualTo(ActionType.RERUN_PIPELINE);
        assertThat(recommended).extracting("type").contains(ActionType.INSPECT_INFRASTRUCTURE);
        assertThat(dependencyActions)
                .extracting("type")
                .contains(ActionType.INSPECT_DEPENDENCY_CONFIGURATION, ActionType.INSPECT_SOURCE_CHANGE)
                .doesNotContain(ActionType.RERUN_PIPELINE);
        assertThat(dependencyActions).allSatisfy(action -> assertThat(action.reason()).isNotBlank());
    }

    @Test
    void resourceAndTestClassificationsMapToSpecificActions() {
        var resource = actions.generate(
                service.triage(List.of(failure(
                        "oom",
                        FailureClassification.RESOURCE_EXHAUSTION,
                        false,
                        null,
                        false,
                        false,
                        TestStabilityClass.INSUFFICIENT_HISTORY))),
                false);
        var test = actions.generate(
                service.triage(List.of(failure(
                        "test",
                        FailureClassification.TEST_FAILURE,
                        false,
                        null,
                        false,
                        false,
                        TestStabilityClass.INSUFFICIENT_HISTORY))),
                false);

        assertThat(resource).extracting("type").contains(ActionType.INSPECT_RESOURCES);
        assertThat(test).extracting("type").contains(ActionType.INSPECT_TEST);
    }

    private PipelineTriageService.Failure failure(
            String fingerprint,
            FailureClassification classification,
            boolean generic,
            ChangeRelevance relevance,
            boolean unchangedPass,
            boolean repeated,
            TestStabilityClass stability) {
        return new PipelineTriageService.Failure(
                UUID.randomUUID(),
                fingerprint,
                classification,
                generic,
                1,
                0,
                relevance,
                stability == null ? 0 : 1,
                stability,
                unchangedPass,
                repeated);
    }
}
