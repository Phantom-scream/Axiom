package com.axiom.integrations.github.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepositoryDto(String name, GitHubOwnerDto owner, @JsonProperty("default_branch") String defaultBranch) {}
