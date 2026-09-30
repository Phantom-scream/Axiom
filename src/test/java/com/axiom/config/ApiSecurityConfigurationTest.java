package com.axiom.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

class ApiSecurityConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);
    @Test void enabledWithoutKeyFailsActualStartup() {
        runner.withPropertyValues("axiom.api-security.enabled=true").run(context -> assertThat(context).hasFailed());
    }
    @Test void enabledWithKeyStartsAndDisabledLocalModeNeedsNoCredentials() {
        runner.withPropertyValues("axiom.api-security.enabled=true","axiom.api-security.key=unit-only-secret")
                .run(context -> {assertThat(context).hasNotFailed();assertThat(context.getBean(ApiSecurityProperties.class).toString()).doesNotContain("unit-only-secret");});
        runner.withPropertyValues("axiom.api-security.enabled=false").run(context -> assertThat(context).hasNotFailed());
    }
    @Configuration @EnableConfigurationProperties(ApiSecurityProperties.class) static class PropertiesConfiguration {}
}
