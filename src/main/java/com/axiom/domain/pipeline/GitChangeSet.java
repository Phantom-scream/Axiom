package com.axiom.domain.pipeline;

import java.util.List;
import java.util.UUID;

public record GitChangeSet(
        UUID pipelineRunId,
        UUID repositoryId,
        String baseSha,
        String headSha,
        Long pullRequestNumber,
        GitProvider provider,
        int totalChangedFiles,
        int totalAdditions,
        int totalDeletions,
        List<ChangedFile> files) {
    public GitChangeSet {
        files = List.copyOf(files);
    }
}
