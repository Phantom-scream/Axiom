package com.axiom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.rerun-history")
public record RerunHistoryProperties(@Min(2) @Max(10_000) Integer maxExecutions) {
    public int effectiveMaxExecutions() { return maxExecutions == null ? 1000 : maxExecutions; }
}
