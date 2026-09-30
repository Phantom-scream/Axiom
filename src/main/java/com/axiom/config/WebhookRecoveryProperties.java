package com.axiom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.webhook-recovery")
public record WebhookRecoveryProperties(
        @Min(1) @Max(100) Integer batchSize,
        @Min(1) @Max(10) Integer maxAttempts,
        @Min(1) @Max(3600) Integer initialBackoffSeconds,
        @Min(1) @Max(86400) Integer staleSeconds,
        @Min(100) @Max(3600000) Long pollMilliseconds) {
    public int batch() { return batchSize == null ? 10 : batchSize; }
    public int attempts() { return maxAttempts == null ? 5 : maxAttempts; }
    public int backoff() { return initialBackoffSeconds == null ? 30 : initialBackoffSeconds; }
    public int stale() { return staleSeconds == null ? 600 : staleSeconds; }
}
