package com.axiom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import com.axiom.domain.analysis.AnalysisStage;
import com.axiom.domain.analysis.AnalysisStageStatus;
import com.axiom.observability.AxiomMetrics;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class OperationalEndpointsTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private AxiomMetrics metrics;

    @Test
    void exposesHealthProbesAndPrometheusWithoutSensitiveTags() throws Exception {
        metrics.analysisStage(
                AnalysisStage.DIAGNOSIS, AnalysisStageStatus.FAILED, Duration.ofMillis(5));
        metrics.githubError("get_pipeline_run", "rate_limit");
        metrics.webhook("received");

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        String prometheus = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(prometheus)
                .contains("axiom_analysis_stage_duration")
                .contains("axiom_analysis_stage_failures_total")
                .contains("axiom_github_errors_total")
                .contains("axiom_webhook_received_total")
                .doesNotContain("fingerprint=")
                .doesNotContain("commit_sha=")
                .doesNotContain("webhookDeliveryId=");
    }
}
