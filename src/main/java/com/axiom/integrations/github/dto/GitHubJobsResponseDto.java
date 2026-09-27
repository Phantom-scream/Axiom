package com.axiom.integrations.github.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true) public record GitHubJobsResponseDto(@com.fasterxml.jackson.annotation.JsonProperty("total_count") int totalCount, List<GitHubJobDto> jobs) {}
