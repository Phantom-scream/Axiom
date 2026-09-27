package com.axiom.integrations.github.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true) public record GitHubJobDto(long id, String name, String status, String conclusion, @JsonProperty("runner_name") String runnerName, @JsonProperty("started_at") Instant startedAt, @JsonProperty("completed_at") Instant completedAt, List<GitHubStepDto> steps) {}
