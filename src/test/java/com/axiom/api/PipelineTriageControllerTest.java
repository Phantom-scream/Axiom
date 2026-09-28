package com.axiom.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiom.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PipelineTriageControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void postPersistsAndAllGetEndpointsReadStoredTriage() throws Exception {
        Fixture fixture = dependencyFixture();

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", fixture.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triageVersion").value("triage-v1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.primaryFailure.failureEventId")
                        .value(fixture.failureId().toString()))
                .andExpect(jsonPath("$.primaryFailure.classification")
                        .value("DEPENDENCY_FAILURE"))
                .andExpect(jsonPath("$.rerunRecommendation").value("NOT_RECOMMENDED"))
                .andExpect(jsonPath("$.actions[0].type")
                        .value("INSPECT_DEPENDENCY_CONFIGURATION"));

        jdbc.update("delete from change_relevance_evidence where relevance_result_id=?", fixture.relevanceId());
        jdbc.update("delete from change_relevance_results where id=?", fixture.relevanceId());

        mockMvc.perform(get("/api/v1/pipeline-runs/{id}/triage", fixture.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeRelevance").value("RELATED"));
        mockMvc.perform(get("/api/v1/pipeline-runs/{id}/triage/failures", fixture.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("PRIMARY"))
                .andExpect(jsonPath("$[0].changeRelevance").value("RELATED"));
        mockMvc.perform(get("/api/v1/pipeline-runs/{id}/triage/actions", fixture.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").isNotEmpty());
        mockMvc.perform(get(
                        "/api/v1/pipeline-runs/{id}/rerun-recommendation", fixture.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation").value("NOT_RECOMMENDED"))
                .andExpect(jsonPath("$.confidence").isNumber());
    }

    @Test
    void missingRunAndNotComputedTriageReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/pipeline-runs/{id}/triage", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        UUID runId = run("FAILURE");
        mockMvc.perform(get("/api/v1/pipeline-runs/{id}/triage", runId))
                .andExpect(status().isNotFound());
    }

    @Test
    void noFailurePipelineReturnsPersistedInsufficientEvidence() throws Exception {
        UUID runId = run("FAILURE");

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/triage", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.primaryFailure").doesNotExist())
                .andExpect(jsonPath("$.rerunRecommendation")
                        .value("INSUFFICIENT_EVIDENCE"))
                .andExpect(jsonPath("$.actions.length()").value(0));
    }

    private Fixture dependencyFixture() {
        UUID runId = run("FAILURE");
        UUID failureId = UUID.randomUUID();
        UUID relevanceId = UUID.randomUUID();
        String fingerprint = "6".repeat(64);
        jdbc.update(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,normalized_message,fingerprint,
                    fingerprint_algorithm,occurrence_count,first_line,last_line)
                values(?,?,?,?,?,?,?,?,?)
                """,
                failureId,
                runId,
                "EXCEPTION",
                "Could not resolve dependency",
                fingerprint,
                "v1",
                1,
                1,
                1);
        jdbc.update(
                """
                insert into failure_diagnoses(
                    id,pipeline_run_id,failure_event_id,fingerprint,classification,confidence,
                    confidence_level,summary,classifier_version)
                values(?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                runId,
                failureId,
                fingerprint,
                "DEPENDENCY_FAILURE",
                .9,
                "HIGH",
                "dependency failure",
                "axiom-classifier-v1");
        jdbc.update(
                """
                insert into change_relevance_results(
                    id,pipeline_run_id,failure_event_id,fingerprint,relevance,confidence,
                    analyzer_version,summary)
                values(?,?,?,?,?,?,?,?)
                """,
                relevanceId,
                runId,
                failureId,
                fingerprint,
                "RELATED",
                .9,
                "change-relevance-v1",
                "related dependency change");
        jdbc.update(
                """
                insert into change_relevance_evidence(
                    id,relevance_result_id,code,priority,weight,description,metadata)
                values(?,?,?,?,?,?,cast(? as jsonb))
                """,
                UUID.randomUUID(),
                relevanceId,
                "DEPENDENCY_FILE_CHANGED",
                "STRONG",
                .9,
                "dependency file changed",
                "{}");
        return new Fixture(runId, failureId, relevanceId);
    }

    private UUID run(String conclusion) {
        UUID repositoryId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "triage-api-owner",
                "repo-" + repositoryId,
                now,
                now);
        jdbc.update(
                """
                insert into pipeline_runs(
                    id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,ingested_at)
                values(?,?,?,?,?,?,?,?)
                """,
                runId,
                repositoryId,
                Math.abs(runId.getMostSignificantBits()),
                "head",
                "COMPLETED",
                conclusion,
                1,
                now);
        return runId;
    }

    private record Fixture(UUID runId, UUID failureId, UUID relevanceId) {}
}
