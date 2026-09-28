package com.axiom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.change-analysis")
public record ChangeAnalysisProperties(@Min(1) @Max(1000) Integer historyLookback) {
    public int effectiveHistoryLookback() {
        return historyLookback == null ? 50 : historyLookback;
    }
}
