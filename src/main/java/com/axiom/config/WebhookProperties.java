package com.axiom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.webhook")
public record WebhookProperties(
        Boolean enabled,
        @Min(1_024) @Max(10_485_760) Integer maxPayloadBytes,
        @Min(1) @Max(32) Integer coreThreads,
        @Min(1) @Max(64) Integer maxThreads,
        @Min(1) @Max(10_000) Integer queueCapacity) {
    public boolean enabledValue() {
        return Boolean.TRUE.equals(enabled);
    }

    public int effectiveMaxPayloadBytes() {
        return maxPayloadBytes == null ? 1_048_576 : maxPayloadBytes;
    }

    public int effectiveCoreThreads() {
        return coreThreads == null ? 2 : coreThreads;
    }

    public int effectiveMaxThreads() {
        return maxThreads == null ? 4 : maxThreads;
    }

    public int effectiveQueueCapacity() {
        return queueCapacity == null ? 100 : queueCapacity;
    }
}
