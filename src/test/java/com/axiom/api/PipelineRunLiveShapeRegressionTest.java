package com.axiom.api;

import com.axiom.IntegrationTestSupport;
import com.axiom.application.pipeline.PipelineIngestionService;
import com.axiom.application.pipeline.PersistedPipelineRun;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class PipelineRunLiveShapeRegressionTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @MockitoBean private PipelineIngestionService ingestion;

    @Test void postgresTimestampSerializesForIngestionAndRetrievalResponses() throws Exception {
        UUID repository=UUID.randomUUID(),run=UUID.randomUUID();
        Instant timestamp=Instant.parse("2026-09-30T20:51:50Z");
        jdbc.update("insert into repositories(id,provider,owner,name) values(?,'GITHUB_ACTIONS','live-regression',?)",repository,repository.toString());
        jdbc.update("insert into pipeline_runs(id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,ingested_at) values(?,?,36775585744,'acdb468','COMPLETED','SUCCESS',1,?)",run,repository,java.sql.Timestamp.from(timestamp));
        when(ingestion.ingest(any())).thenReturn(new PersistedPipelineRun(run,1,0,0,timestamp));
        mvc.perform(get("/api/v1/pipeline-runs/{id}",run)).andExpect(status().isOk()).andExpect(jsonPath("$.ingestedAt").value(timestamp.toString()));
        mvc.perform(post("/api/v1/pipeline-runs/ingest").contentType("application/json").content("{\"provider\":\"GITHUB_ACTIONS\",\"repositoryOwner\":\"live-regression\",\"repositoryName\":\"repo\",\"externalRunId\":36775585744}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(run.toString())).andExpect(jsonPath("$.ingestedAt").value(timestamp.toString()));
    }
}
