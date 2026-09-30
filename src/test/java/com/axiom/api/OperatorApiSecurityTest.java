package com.axiom.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.axiom.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties={"axiom.api-security.enabled=true","axiom.api-security.key=integration-operator-key"})
class OperatorApiSecurityTest extends IntegrationTestSupport {
    @Autowired private MockMvc mvc;
    @Test void protectsManagementAndMetricsButKeepsProbesAndWebhookSeparate() throws Exception {
        mvc.perform(get("/api/v1/repositories/00000000-0000-0000-0000-000000000001/health")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("X-Axiom-Api-Key","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("X-Axiom-Api-Key","integration-operator-key")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/webhooks/github").header("X-GitHub-Event","ping").header("X-GitHub-Delivery","api-key-independent").content("{}"))
                .andExpect(status().isConflict());
    }
    @Test void enabledSecurityFailsWithoutSecretAndNeverRendersKey() {
        assertThatThrownBy(() -> new com.axiom.config.ApiSecurityProperties(true,null).validate()).hasMessageContaining("API key");
        org.assertj.core.api.Assertions.assertThat(new com.axiom.config.ApiSecurityProperties(true,"secret-value").toString()).doesNotContain("secret-value");
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"trends","incidents","failures/example/lifecycle","tests/reliability","hotspots"})
    void historicalEndpointsAlsoRequireOperatorAuthentication(String endpoint) throws Exception {
        String path="/api/v1/repositories/00000000-0000-0000-0000-000000000001/"+endpoint;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Axiom-Api-Key","integration-operator-key")).andExpect(status().isNotFound());
    }
}
