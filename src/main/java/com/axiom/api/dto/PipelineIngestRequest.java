package com.axiom.api.dto;
import com.axiom.domain.pipeline.CiProviderType;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Positive;
public record PipelineIngestRequest(@NotNull CiProviderType provider,@NotBlank String repositoryOwner,@NotBlank String repositoryName,@Positive long externalRunId) {}
