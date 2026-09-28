package com.axiom.domain.triage;

public record RecommendedAction(
        int priority, ActionType type, String description, String reason) {}
