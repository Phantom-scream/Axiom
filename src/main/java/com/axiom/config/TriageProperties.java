package com.axiom.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.triage")
public record TriageProperties(
        String version,
        @Min(1) @Max(10) Integer maxActions,
        @DecimalMin("0.0") @DecimalMax("1.0") Double primaryMinimumScore,
        @DecimalMin("0.0") @DecimalMax("1.0") Double primaryDominanceMargin) {
    public String effectiveVersion() {
        return version == null || version.isBlank() ? "triage-v1" : version;
    }

    public int effectiveMaxActions() {
        return maxActions == null ? 3 : maxActions;
    }

    public double effectivePrimaryMinimumScore() {
        return primaryMinimumScore == null ? .6 : primaryMinimumScore;
    }

    public double effectivePrimaryDominanceMargin() {
        return primaryDominanceMargin == null ? .1 : primaryDominanceMargin;
    }
}
