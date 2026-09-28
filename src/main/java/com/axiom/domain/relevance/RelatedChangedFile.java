package com.axiom.domain.relevance;

import java.util.UUID;

public record RelatedChangedFile(UUID changedFileId, String path, String relationship, double weight) {}
