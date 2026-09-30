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
    private final com.axiom.config.WebhookRecoveryProperties recovery;
    private final com.axiom.observability.AxiomMetrics metrics;

    public GitHubWebhookDeliveryService(JdbcTemplate jdbc, Clock clock,
            com.axiom.config.WebhookRecoveryProperties recovery, com.axiom.observability.AxiomMetrics metrics) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.recovery = recovery;
        this.metrics = metrics;
    }

    @Transactional
    public boolean acceptWorkflow(GitHubWorkflowAutomationService.WorkflowRunCommand command) {
        boolean inserted = accept(command.deliveryId(), "workflow_run", command.owner() + "/" + command.repository(), command.externalRunId(), "RECEIVED");
        if (inserted) jdbc.update("update github_webhook_deliveries set status='ACCEPTED',run_attempt=?,has_pull_request=?,next_attempt_at=? where delivery_id=?",
                command.runAttempt(), command.hasPullRequest(), Timestamp.from(clock.instant()), command.deliveryId());
        return inserted;
    }

    /** Holds a dedicated session advisory lock for the entire work unit, not a long transaction. */
    public void runClaimed(GitHubWorkflowAutomationService.WorkflowRunCommand command, Runnable work) {
        try (var connection = java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            String deliveryLock = "axiom-delivery:" + command.deliveryId();
            String runLock = "axiom-run:" + command.owner() + "/" + command.repository() + ":" + command.externalRunId() + ":" + command.runAttempt();
            boolean deliveryHeld = lock(connection, deliveryLock, true);
            if (!deliveryHeld) return;
            boolean runHeld = false;
            try {
                runHeld = lock(connection, runLock, true);
                if (!runHeld) return;
                expireExhausted(command.deliveryId());
                int claimed = jdbc.update("""
                        update github_webhook_deliveries set status='PROCESSING',attempt_count=attempt_count+1,
                          processing_started_at=?,last_attempted_at=?,updated_at=?,next_attempt_at=null
                        where delivery_id=? and attempt_count<? and (
                          status='ACCEPTED' or (status='PROCESSING' and coalesce(processing_started_at,received_at)<=?)
                          or (status in ('FAILED','COMPLETED_WITH_STAGE_FAILURE','COMPLETED_WITH_PUBLICATION_FAILURE') and next_attempt_at<=?))
                        """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()),
                        command.deliveryId(), recovery.attempts(), Timestamp.from(clock.instant().minusSeconds(recovery.stale())), Timestamp.from(clock.instant()));
                if (claimed == 1) work.run();
            } finally {
                try {
                    if (runHeld) lock(connection, runLock, false);
                    lock(connection, deliveryLock, false);
                } catch (java.sql.SQLException unlockFailure) {
                    // Never return a session with a possibly retained lock to the pool.
                    connection.abort(Runnable::run);
                    throw unlockFailure;
                }
            }
        } catch (java.sql.SQLException exception) {
            throw new org.springframework.dao.DataAccessResourceFailureException("Webhook claim connection failed.", exception);
        }
    }

    private boolean lock(java.sql.Connection connection, String identity, boolean acquire) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement(acquire ? "select pg_try_advisory_lock(hashtextextended(?,0))" : "select pg_advisory_unlock(hashtextextended(?,0))")) {
            statement.setString(1, identity);
            try (var result = statement.executeQuery()) { result.next(); return result.getBoolean(1); }
        }
    }

    private void expireExhausted(String deliveryId) {
        int changed = jdbc.update("""
                update github_webhook_deliveries set status='FAILED',next_attempt_at=null,
                  processed_at=?,updated_at=?,error_code='RETRY_EXHAUSTED',error_message='Webhook processing retry limit reached.'
                where delivery_id=? and attempt_count>=? and (status='ACCEPTED'
                  or status='PROCESSING' or next_attempt_at is not null)
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant()), deliveryId, recovery.attempts());
        if (changed > 0) metrics.webhook("processing.dead");
    }

    public void retry(String deliveryId, boolean retryable) {
        Integer attempts = jdbc.queryForObject("select attempt_count from github_webhook_deliveries where delivery_id=?", Integer.class, deliveryId);
        boolean retry = retryable && attempts != null && attempts < recovery.attempts();
        long delay = Math.min(3600L, recovery.backoff() * (1L << Math.min(attempts == null ? 0 : attempts, 10)));
        jdbc.update("update github_webhook_deliveries set next_attempt_at=?,updated_at=? where delivery_id=?",
                retry ? Timestamp.from(clock.instant().plusSeconds(delay)) : null, Timestamp.from(clock.instant()), deliveryId);
        metrics.webhook(retry ? "processing.retries" : "processing.dead");
    }

    public java.util.List<GitHubWorkflowAutomationService.WorkflowRunCommand> recoverable() {
        // Legacy accepted rows without recoverable metadata cannot be invented safely.
        jdbc.update("""
                update github_webhook_deliveries set status='FAILED',next_attempt_at=null,error_code='MISSING_WORK_METADATA',
                  error_message='Legacy delivery lacks workflow attempt metadata; ingest explicitly.',updated_at=?
                where id in (select id from github_webhook_deliveries
                  where status in ('ACCEPTED','PROCESSING') and run_attempt is null order by received_at,id limit ?)
                """, Timestamp.from(clock.instant()),recovery.batch());
        return jdbc.query("""
                select delivery_id,repository_full_name,external_run_id,run_attempt,has_pull_request
                from github_webhook_deliveries where event_type='workflow_run' and run_attempt is not null and (
                  status='ACCEPTED' or (status='PROCESSING' and coalesce(processing_started_at,received_at)<=?)
                  or (status in ('FAILED','COMPLETED_WITH_STAGE_FAILURE','COMPLETED_WITH_PUBLICATION_FAILURE') and next_attempt_at<=?))
                order by coalesce(next_attempt_at,received_at),delivery_id limit ?
                """, (rs,row) -> {
                    String[] repository = rs.getString("repository_full_name").split("/",2);
                    return new GitHubWorkflowAutomationService.WorkflowRunCommand(rs.getString("delivery_id"),repository[0],repository[1],rs.getLong("external_run_id"),rs.getInt("run_attempt"),rs.getBoolean("has_pull_request"));
                }, Timestamp.from(clock.instant().minusSeconds(recovery.stale())),Timestamp.from(clock.instant()),recovery.batch());
    }

    public Optional<UUID> persistedRun(String deliveryId) {
        return jdbc.query("select pipeline_run_id from github_webhook_deliveries where delivery_id=?", rs -> rs.next() ? Optional.ofNullable(rs.getObject(1, UUID.class)) : Optional.empty(), deliveryId);
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
                set status=?,processed_at=?,error_code=?,error_message=?,updated_at=?,next_attempt_at=null
                where delivery_id=?
                """,
                status,
                Timestamp.from(clock.instant()),
                errorCode,
                bounded(errorMessage),
                Timestamp.from(clock.instant()),
                deliveryId);
    }

    @Transactional
    public void finish(String deliveryId, String status, String errorCode, String errorMessage, boolean retryable) {
        complete(deliveryId, status, errorCode, errorMessage);
        if (errorCode != null) retry(deliveryId, retryable);
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
