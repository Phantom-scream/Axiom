package com.axiom.api.dto;

import com.axiom.domain.pipeline.GitChangeSet;
import com.axiom.domain.pipeline.GitProvider;
import java.util.List;
import java.util.UUID;

public record GitChangeSetResponse(
        UUID pipelineRunId,
        UUID repositoryId,
        String baseSha,
        String headSha,
        Long pullRequestNumber,
        GitProvider provider,
        int totalChangedFiles,
        int totalAdditions,
        int totalDeletions,
        List<ChangedFileResponse> files) {
    public static GitChangeSetResponse from(GitChangeSet changeSet) {
        return new GitChangeSetResponse(
                changeSet.pipelineRunId(),
                changeSet.repositoryId(),
                changeSet.baseSha(),
                changeSet.headSha(),
                changeSet.pullRequestNumber(),
                changeSet.provider(),
                changeSet.totalChangedFiles(),
                changeSet.totalAdditions(),
                changeSet.totalDeletions(),
                changeSet.files().stream().map(ChangedFileResponse::from).toList());
    }
}
