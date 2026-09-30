package com.axiom.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("axiom.api-security")
public record ApiSecurityProperties(Boolean enabled, String key) {
    public boolean enabledValue() { return Boolean.TRUE.equals(enabled); }
    @PostConstruct public void validate() {
        if (enabledValue() && (key == null || key.isBlank())) throw new IllegalStateException("Operator API security requires an API key.");
        if (enabledValue() && (key.length()<16 || key.length()>4096)) throw new IllegalStateException("Operator API key must have between 16 and 4096 characters.");
    }
    @Override public String toString() { return "ApiSecurityProperties[enabled=" + enabledValue() + ",key=<redacted>]"; }
}
