package com.axiom.application.pipeline;
import com.axiom.domain.pipeline.CiProviderType;
public record PipelineRunReference(CiProviderType provider, String repositoryOwner, String repositoryName, long externalRunId) {}
