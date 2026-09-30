package com.axiom.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.history")
public record HistoricalIntelligenceProperties(
        @Min(1) @Max(3650) Integer maxDays, @Min(1) @Max(10000) Integer maxRuns,
        @Min(1) @Max(100) Integer maxResults, @Min(1) @Max(100) Integer activeRecentRuns,
        @Min(1) @Max(100) Integer resolvedCleanRuns, @Min(2) @Max(100) Integer minimumRecurringRuns) {
    public int days() { return maxDays == null ? 730 : maxDays; }
    public int runs() { return maxRuns == null ? 1000 : maxRuns; }
    public int results() { return maxResults == null ? 100 : maxResults; }
    public int active() { return activeRecentRuns == null ? 5 : activeRecentRuns; }
    public int resolved() { return resolvedCleanRuns == null ? 3 : resolvedCleanRuns; }
    public int recurring() { return minimumRecurringRuns == null ? 2 : minimumRecurringRuns; }
}
