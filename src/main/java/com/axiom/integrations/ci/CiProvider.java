package com.axiom.integrations.ci;

import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.PipelineJob;
import com.axiom.domain.pipeline.PipelineRun;
import java.util.List;

public interface CiProvider {
    CiProviderType providerType();
    PipelineRun fetchRun(String repositoryOwner, String repositoryName, long runId);
    List<PipelineJob> fetchJobs(String repositoryOwner, String repositoryName, long runId);
    byte[] downloadRunLogs(String repositoryOwner, String repositoryName, long runId);
    default PipelineRun fetchRunAttempt(String owner, String repository, long runId, int attempt) {
        throw new IllegalArgumentException("Run-attempt ingestion is unsupported for this provider.");
    }
    default byte[] downloadRunAttemptLogs(String owner, String repository, long runId, int attempt) {
        throw new IllegalArgumentException("Run-attempt logs are unsupported for this provider.");
    }
}
