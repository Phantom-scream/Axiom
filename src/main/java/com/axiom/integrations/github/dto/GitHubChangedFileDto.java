package com.axiom.integrations.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubChangedFileDto(
        String filename,
        @JsonProperty("previous_filename") String previousFilename,
        String status,
        int additions,
        int deletions,
        int changes) {}
