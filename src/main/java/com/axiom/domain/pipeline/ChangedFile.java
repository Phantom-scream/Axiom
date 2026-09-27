package com.axiom.domain.pipeline;

public record ChangedFile(
        String path,
        String previousPath,
        ChangeType changeType,
        FileCategory fileCategory,
        String moduleHint,
        int additions,
        int deletions,
        int changes) {}
