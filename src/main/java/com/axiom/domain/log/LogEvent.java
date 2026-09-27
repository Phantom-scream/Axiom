package com.axiom.domain.log; import java.time.Instant; public record LogEvent(Instant timestamp,LogSeverity severity,String message,String normalizedMessage,int lineStart,int lineEnd) {}
