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
class PipelineChangeRelevanceControllerTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void analyzesAndRetrievesPersistedRelevanceWithoutImplicitRecomputation() throws Exception {
        Fixture fixture = fixture(true);

        mockMvc.perform(post("/api/v1/pipeline-runs/{id}/changes/analyze", fixture.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipelineRunId").value(fixture.runId().toString()))
                .andExpect(jsonPath("$.analyzerVersion").value("change-relevance-v1"))
                .andExpect(jsonPath("$.analyzedFailures").value(1))
                .andExpect(jsonPath("$.results.RELATED").value(1));

        jdbc.update("delete from git_change_sets where pipeline_run_id=?", fixture.runId());

        mockMvc.perform(get("/api/v1/pipeline-runs/{id}/relevance", fixture.runId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fingerprint").value(fixture.fingerprint()))
                .andExpect(jsonPath("$[0].relevance").value("RELATED"))
                .andExpect(jsonPath("$[0].evidence[?(@.code == 'DEPENDENCY_FILE_CHANGED')]").exists());

        mockMvc.perform(get(
                        "/api/v1/pipeline-runs/{id}/relevance/{fingerprint}",
                        fixture.runId(),
                        fixture.fingerprint()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureEventId").value(fixture.failureId().toString()))
                .andExpect(jsonPath("$.confidence").value(1.0));
    }

    @Test
    void missingRunMissingChangesAndUnknownFingerprintUseControlledErrors() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/pipeline-runs/{id}/changes/analyze", UUID.randomUUID()))
                .andExpect(status().isNotFound());

        Fixture withoutChanges = fixture(false);
        mockMvc.perform(post(
                        "/api/v1/pipeline-runs/{id}/changes/analyze", withoutChanges.runId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ANALYSIS_PREREQUISITE_MISSING"));

        mockMvc.perform(get(
                        "/api/v1/pipeline-runs/{id}/relevance/{fingerprint}",
                        withoutChanges.runId(),
                        "missing"))
                .andExpect(status().isNotFound());
    }

    private Fixture fixture(boolean withChanges) {
        UUID repositoryId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID failureId = UUID.randomUUID();
        String fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(
                "insert into repositories(id,provider,owner,name,created_at,updated_at) values(?,?,?,?,?,?)",
                repositoryId,
                "GITHUB_ACTIONS",
                "relevance-api-owner",
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
                "FAILURE",
                1,
                now);
        jdbc.update(
                """
                insert into failure_events(
                    id,pipeline_run_id,event_type,normalized_message,fingerprint,
                    fingerprint_algorithm,occurrence_count)
                values(?,?,?,?,?,?,?)
                """,
                failureId,
                runId,
                "EXCEPTION",
                "Could not resolve dependency",
                fingerprint,
                "v1",
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
        if (withChanges) {
            UUID setId = UUID.randomUUID();
            jdbc.update(
                    """
                    insert into git_change_sets(
                        id,pipeline_run_id,base_sha,head_sha,total_changed_files,provider)
                    values(?,?,?,?,?,?)
                    """,
                    setId,
                    runId,
                    "base",
                    "head",
                    1,
                    "GITHUB");
            jdbc.update(
                    """
                    insert into changed_files(
                        id,change_set_id,path,change_type,file_category,additions,deletions,changes)
                    values(?,?,?,?,?,?,?,?)
                    """,
                    UUID.randomUUID(),
                    setId,
                    "pom.xml",
                    "MODIFIED",
                    "DEPENDENCY_MANIFEST",
                    1,
                    0,
                    1);
        }
        return new Fixture(runId, failureId, fingerprint);
    }

    private record Fixture(UUID runId, UUID failureId, String fingerprint) {}
}
