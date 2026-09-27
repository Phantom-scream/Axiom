package com.axiom.api;

import com.axiom.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DemoAnalysisControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Test void demoReturnsEvidenceBackedDeterministicDiagnosis() throws Exception {
        mockMvc.perform(post("/api/v1/analysis/demo").contentType(MediaType.APPLICATION_JSON).content("{\"log\":\"AssertionError: expected true\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnosis.classification").value("TEST_FAILURE")).andExpect(jsonPath("$.diagnosis.confidence").value(0.85)).andExpect(jsonPath("$.diagnosis.evidence[0].code").value("TEST_ASSERTION_FAILED"));
    }
}
