package com.axiom.integrations.github;

import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.PipelineJob;
import com.axiom.domain.pipeline.PipelineRun;
import com.axiom.integrations.ci.CiProvider;
import com.axiom.integrations.github.client.GitHubApiClient;
import com.axiom.integrations.github.mapper.GitHubRunMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GitHubActionsProvider implements CiProvider {
    private final GitHubApiClient client; private final GitHubRunMapper mapper;
    public GitHubActionsProvider(GitHubApiClient client, GitHubRunMapper mapper) { this.client = client; this.mapper = mapper; }
    @Override public CiProviderType providerType() { return CiProviderType.GITHUB_ACTIONS; }
    @Override public PipelineRun fetchRun(String owner, String repository, long runId) { var jobs = fetchJobs(owner, repository, runId); return mapper.run(client.workflowRun(owner, repository, runId), owner, repository, jobs); }
    @Override public List<PipelineJob> fetchJobs(String owner, String repository, long runId) { return client.jobs(owner, repository, runId).stream().map(mapper::job).toList(); }
    @Override public byte[] downloadRunLogs(String owner, String repository, long runId) { return client.logs(owner, repository, runId); }
}
