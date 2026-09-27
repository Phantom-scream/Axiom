package com.axiom.logstorage;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component public class DatabaseLogStorage implements LogStorage {
    private final JdbcTemplate jdbc; public DatabaseLogStorage(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public LogReference store(UUID runId, byte[] content) { if(content.length > 50_000_000) throw new IllegalArgumentException("Downloaded log archive exceeds 50 MB limit."); UUID id=UUID.randomUUID(); String hash=sha256(content); jdbc.update("insert into pipeline_logs(id,pipeline_run_id,storage_type,content,size_bytes,sha256) values (?,?,?,?,?,?) on conflict (pipeline_run_id) do update set content=excluded.content,size_bytes=excluded.size_bytes,sha256=excluded.sha256,created_at=current_timestamp", id,runId,"DATABASE",content,(long)content.length,hash); return new LogReference(id, content.length, hash); }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); } }
}
