package com.axiom.logstorage;

import com.axiom.config.OperationalLimitsProperties;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseLogStorage implements LogStorage {
    private final JdbcTemplate jdbc;
    private final OperationalLimitsProperties limits;

    public DatabaseLogStorage(JdbcTemplate jdbc, OperationalLimitsProperties limits) {
        this.jdbc = jdbc;
        this.limits = limits;
    }

    @Override
    public LogReference store(UUID runId, byte[] content) {
        if (content.length > limits.effectiveStoredLogBytes()) {
            throw new IllegalArgumentException(
                    "Downloaded log archive exceeds the configured storage limit.");
        }
        UUID id = UUID.randomUUID();
        String hash = sha256(content);
        jdbc.update(
                """
                insert into pipeline_logs(
                    id,pipeline_run_id,storage_type,content,size_bytes,sha256)
                values (?,?,?,?,?,?)
                on conflict (pipeline_run_id) do update set
                    content=excluded.content,size_bytes=excluded.size_bytes,
                    sha256=excluded.sha256,created_at=current_timestamp
                """,
                id,
                runId,
                "DATABASE",
                content,
                (long) content.length,
                hash);
        return new LogReference(id, content.length, hash);
    }

    @Override
    public Optional<byte[]> load(UUID runId) {
        return jdbc.query(
                "select content from pipeline_logs where pipeline_run_id=?",
                rs -> rs.next() ? Optional.of(rs.getBytes(1)) : Optional.empty(),
                runId);
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
