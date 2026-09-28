package com.axiom.integrations.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubCreateCheckRunRequestDto(
        String name,
        @JsonProperty("head_sha") String headSha,
        String status,
        String conclusion,
        GitHubCheckOutputDto output) {}
