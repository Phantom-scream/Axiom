package com.axiom.application.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.axiom.domain.pipeline.FileCategory;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChangedFileClassifierTest {
    private final ChangedFileClassifier classifier = new ChangedFileClassifier();

    @ParameterizedTest
    @MethodSource("paths")
    void classifiesRepresentativeRepositoryPaths(String path, FileCategory expected) {
        assertThat(classifier.classify(path)).isEqualTo(expected);
    }

    static Stream<Arguments> paths() {
        return Stream.of(
                Arguments.of("src/main/java/com/example/PaymentService.java", FileCategory.PRODUCTION_SOURCE),
                Arguments.of("src/test/java/com/example/PaymentServiceTest.java", FileCategory.TEST_SOURCE),
                Arguments.of("web/src/payment.ts", FileCategory.PRODUCTION_SOURCE),
                Arguments.of("tests/test_payment.py", FileCategory.TEST_SOURCE),
                Arguments.of("package.json", FileCategory.DEPENDENCY_MANIFEST),
                Arguments.of("package-lock.json", FileCategory.DEPENDENCY_LOCKFILE),
                Arguments.of("build.gradle.kts", FileCategory.BUILD_CONFIGURATION),
                Arguments.of(".github/workflows/ci.yml", FileCategory.CI_CONFIGURATION),
                Arguments.of("Dockerfile", FileCategory.INFRASTRUCTURE_CONFIGURATION),
                Arguments.of("kubernetes/deployment.yaml", FileCategory.INFRASTRUCTURE_CONFIGURATION),
                Arguments.of("helm/axiom/values.yaml", FileCategory.INFRASTRUCTURE_CONFIGURATION),
                Arguments.of("terraform/main.tf", FileCategory.INFRASTRUCTURE_CONFIGURATION),
                Arguments.of("src/main/resources/db/migration/V10__x.sql", FileCategory.DATABASE_MIGRATION),
                Arguments.of("src/main/resources/application.yml", FileCategory.APPLICATION_CONFIGURATION),
                Arguments.of("README.md", FileCategory.DOCUMENTATION),
                Arguments.of("assets/logo.svg", FileCategory.UNKNOWN));
    }

    @ParameterizedTest
    @MethodSource("modulePaths")
    void derivesBoundedModuleHints(String path, String expected) {
        assertThat(classifier.moduleHint(path)).isEqualTo(expected);
    }

    static Stream<Arguments> modulePaths() {
        return Stream.of(
                Arguments.of(
                        "src/main/java/com/example/PaymentService.java", "src/main/java/com/example"),
                Arguments.of("README.md", null));
    }
}
