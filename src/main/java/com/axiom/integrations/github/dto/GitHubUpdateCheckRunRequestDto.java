package com.axiom.integrations.github.dto;

public record GitHubUpdateCheckRunRequestDto(
        String name, String status, String conclusion, GitHubCheckOutputDto output) {}
