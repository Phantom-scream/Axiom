package com.axiom.api.dto;

import com.axiom.domain.pipeline.ChangeType;
import com.axiom.domain.pipeline.ChangedFile;
import com.axiom.domain.pipeline.FileCategory;

public record ChangedFileResponse(
        String path,
        String previousPath,
        ChangeType changeType,
        FileCategory fileCategory,
        String moduleHint,
        int additions,
        int deletions,
        int changes) {
    public static ChangedFileResponse from(ChangedFile file) {
        return new ChangedFileResponse(
                file.path(),
                file.previousPath(),
                file.changeType(),
                file.fileCategory(),
                file.moduleHint(),
                file.additions(),
                file.deletions(),
                file.changes());
    }
}
