package com.axiom.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DemoAnalysisRequest(@NotBlank @Size(max = 100_000) String log) {}
