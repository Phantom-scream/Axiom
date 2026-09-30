package com.axiom.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsSafeDefaultsWithoutCredentials() {
        var github = properties(null, false, false, Duration.ofSeconds(1));
        var webhook = new WebhookProperties(false, null, null, null, null);

        assertThatCode(() -> new ProductionConfigurationValidator(github, webhook).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void requiresSecretWhenWebhooksAreEnabledWithoutDisclosingIt() {
        var validator = new ProductionConfigurationValidator(
                properties(null, false, false, Duration.ofSeconds(1)),
                new WebhookProperties(true, null, null, null, null));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("webhook secret")
                .hasMessageNotContaining("token");
    }

    @Test
    void rejectsInvalidTimeoutAndHistoryBoundsRemainValidated() {
        var validator = new ProductionConfigurationValidator(
                properties("secret", false, false, Duration.ZERO),
                new WebhookProperties(true, null, null, null, null));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeout must be positive");
    }

    @Test
    void automaticPublishingRequiresToken() {
        var validator = new ProductionConfigurationValidator(
                properties("secret", true, false, Duration.ofSeconds(1)),
                new WebhookProperties(true, null, null, null, null));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GitHub token");
    }

    @Test
    void historyLimitsAndSecretRenderingAreSafe() {
        try (var validator = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            org.assertj.core.api.Assertions.assertThat(validator.getValidator().validate(new RerunHistoryProperties(0))).isNotEmpty();
            org.assertj.core.api.Assertions.assertThat(validator.getValidator().validate(new ChangeAnalysisProperties(1001))).isNotEmpty();
        }
        org.assertj.core.api.Assertions.assertThat(properties("do-not-render", false, false, Duration.ofSeconds(1)).toString())
                .doesNotContain("do-not-render");
    }

    private GitHubProperties properties(
            String webhookSecret,
            boolean autoCheck,
            boolean autoComment,
            Duration connectTimeout) {
        return new GitHubProperties(
                null,
                "https://api.github.com",
                webhookSecret,
                autoCheck,
                autoComment,
                connectTimeout,
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                3,
                Duration.ofMillis(10),
                Duration.ofMillis(20));
    }
}
