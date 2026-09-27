package com.axiom.logstorage;
import java.util.UUID;
public record LogReference(UUID id, long sizeBytes, String sha256) {}
