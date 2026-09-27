package com.axiom.integrations.github.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true) public record GitHubOwnerDto(String login) {}
