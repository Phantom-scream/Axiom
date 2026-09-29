package com.axiom.integrations.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueCommentResponseDto(long id, @JsonProperty("html_url") String htmlUrl) {}
