package com.axiom.integrations.github.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
@JsonIgnoreProperties(ignoreUnknown = true) public record GitHubStepDto(int number, String name, String status, String conclusion, @JsonProperty("started_at") Instant startedAt, @JsonProperty("completed_at") Instant completedAt) {}
