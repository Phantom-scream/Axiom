package com.axiom.api.dto;
import java.time.Instant; import java.util.UUID;
public record PipelineRunResponse(UUID id,String provider,String repositoryOwner,String repositoryName,long externalRunId,int runAttempt,String status,String conclusion,String commitSha,String branch,Long pullRequestNumber,int jobCount,int stepCount,Instant ingestedAt) {}
