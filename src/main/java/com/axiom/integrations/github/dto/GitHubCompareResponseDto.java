package com.axiom.integrations.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCompareResponseDto(
        String status,
        @JsonProperty("ahead_by") int aheadBy,
        @JsonProperty("behind_by") int behindBy,
        @JsonProperty("base_commit") GitHubCompareCommitDto baseCommit,
        @JsonProperty("merge_base_commit") GitHubCompareCommitDto mergeBaseCommit,
        List<GitHubCompareCommitDto> commits,
        List<GitHubChangedFileDto> files) {}
