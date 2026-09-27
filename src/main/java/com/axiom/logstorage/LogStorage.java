package com.axiom.logstorage;
import java.util.UUID;
public interface LogStorage { LogReference store(UUID pipelineRunId, byte[] content); }
