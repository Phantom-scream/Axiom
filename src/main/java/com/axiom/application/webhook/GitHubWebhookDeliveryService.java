package com.axiom.application.webhook;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubWebhookDeliveryService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GitHubWebhookDeliveryService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public boolean accept(
            String deliveryId,
            String eventType,
            String repositoryFullName,
            Long externalRunId,
            String status) {
        return jdbc.update(
                        """
                        insert into github_webhook_deliveries(
                            id,delivery_id,event_type,repository_full_name,external_run_id,
                            status,received_at)
                        values(?,?,?,?,?,?,?)
                        on conflict(delivery_id) do nothing
                        """,
                        UUID.randomUUID(),
                        deliveryId,
                        eventType,
                        repositoryFullName,
                        externalRunId,
                        status,
                        Timestamp.from(clock.instant()))
                == 1;
    }

    @Transactional
    public void processing(String deliveryId) {
        jdbc.update(
                "update github_webhook_deliveries set status='PROCESSING',error_code=null,error_message=null where delivery_id=?",
                deliveryId);
    }

    @Transactional
    public void pipelineRun(String deliveryId, UUID pipelineRunId) {
        jdbc.update(
                "update github_webhook_deliveries set pipeline_run_id=? where delivery_id=?",
                pipelineRunId,
                deliveryId);
    }

    @Transactional
    public void complete(String deliveryId, String status, String errorCode, String errorMessage) {
        jdbc.update(
                """
                update github_webhook_deliveries
                set status=?,processed_at=?,error_code=?,error_message=?
                where delivery_id=?
                """,
                status,
                Timestamp.from(clock.instant()),
                errorCode,
                bounded(errorMessage),
                deliveryId);
    }

    public Optional<String> status(String deliveryId) {
        return jdbc.query(
                "select status from github_webhook_deliveries where delivery_id=?",
                rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(),
                deliveryId);
    }

    private String bounded(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(500, value.length()));
    }
}
