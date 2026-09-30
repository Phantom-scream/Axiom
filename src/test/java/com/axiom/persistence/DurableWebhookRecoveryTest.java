package com.axiom.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.axiom.IntegrationTestSupport;
import com.axiom.application.webhook.GitHubWebhookDeliveryService;
import com.axiom.application.webhook.GitHubWorkflowAutomationService.WorkflowRunCommand;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DurableWebhookRecoveryTest extends IntegrationTestSupport {
    @Autowired private GitHubWebhookDeliveryService deliveries;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void outcomeAndRetryScheduleCommitOrRollbackTogether() {
        var command=command("durable-atomic-finish"); deliveries.acceptWorkflow(command);
        jdbc.update("update github_webhook_deliveries set status='PROCESSING',attempt_count=1 where delivery_id=?",command.deliveryId());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            deliveries.finish(command.deliveryId(),"FAILED","TEMPORARY","Safe summary",true);
            transaction.setRollbackOnly();
        });
        assertThat(deliveries.status(command.deliveryId())).contains("PROCESSING");
        deliveries.finish(command.deliveryId(),"FAILED","TEMPORARY","Safe summary",true);
        assertThat(deliveries.status(command.deliveryId())).contains("FAILED");
        assertThat(jdbc.queryForObject("select next_attempt_at is not null from github_webhook_deliveries where delivery_id=?",Boolean.class,command.deliveryId())).isTrue();
    }
    private WorkflowRunCommand command(String id) { return new WorkflowRunCommand(id,"recovery","repo",7755,2,true); }
    @Test void acceptedAndStaleWorkRecoverFromOnlyPersistedMetadata() {
        var accepted=command("durable-accepted");
        assertThat(deliveries.acceptWorkflow(accepted)).isTrue();
        assertThat(deliveries.acceptWorkflow(accepted)).isFalse();
        assertThat(deliveries.recoverable()).contains(accepted);
        recoverThroughNewWorker();
        assertThat(deliveries.recoverable()).doesNotContain(accepted);
        var stale=command("durable-stale"); deliveries.acceptWorkflow(stale);
        jdbc.update("update github_webhook_deliveries set status='PROCESSING',attempt_count=1,processing_started_at=current_timestamp-interval '1 hour' where delivery_id=?",stale.deliveryId());
        assertThat(deliveries.recoverable()).contains(stale);
        recoverThroughNewWorker();
        assertThat(jdbc.queryForObject("select attempt_count from github_webhook_deliveries where delivery_id=?",Integer.class,stale.deliveryId())).isEqualTo(2);
    }
    @Test void concurrentWorkersCannotExecuteTwiceAndStaleLockCannotBeStolen() throws Exception {
        var command=command("durable-concurrent"); deliveries.acceptWorkflow(command);
        AtomicInteger count=new AtomicInteger(); var claimed=new CountDownLatch(1); var release=new CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> deliveries.runClaimed(command,() -> {count.incrementAndGet();claimed.countDown();try {release.await(5,TimeUnit.SECONDS);} catch(InterruptedException e){Thread.currentThread().interrupt();} deliveries.complete(command.deliveryId(),"COMPLETED",null,null);}));
            assertThat(claimed.await(5,TimeUnit.SECONDS)).isTrue();
            jdbc.update("update github_webhook_deliveries set processing_started_at=current_timestamp-interval '1 hour' where delivery_id=?",command.deliveryId());
            var second=pool.submit(() -> deliveries.runClaimed(command,count::incrementAndGet)); second.get(5,TimeUnit.SECONDS);
            release.countDown(); first.get(5,TimeUnit.SECONDS);
            assertThat(count).hasValue(1);
        }
    }
    @Test void retryBackoffAndExhaustionAreDurableAndBounded() {
        var command=command("durable-retry"); deliveries.acceptWorkflow(command);
        jdbc.update("update github_webhook_deliveries set attempt_count=4 where delivery_id=?",command.deliveryId());
        deliveries.runClaimed(command,() -> { deliveries.complete(command.deliveryId(),"FAILED","TEMPORARY","Safe summary"); deliveries.retry(command.deliveryId(),true); });
        assertThat(jdbc.queryForObject("select next_attempt_at is null from github_webhook_deliveries where delivery_id=?",Boolean.class,command.deliveryId())).isTrue();
        deliveries.runClaimed(command,() -> { throw new AssertionError("exhausted delivery executed"); });
        assertThat(deliveries.recoverable()).doesNotContain(command);
        var retry=command("durable-transient"); deliveries.acceptWorkflow(retry);
        deliveries.runClaimed(retry,() -> { deliveries.complete(retry.deliveryId(),"FAILED","TEMPORARY","Safe summary"); deliveries.retry(retry.deliveryId(),true); });
        assertThat(deliveries.recoverable()).doesNotContain(retry);
        jdbc.update("update github_webhook_deliveries set next_attempt_at=current_timestamp-interval '1 second' where delivery_id=?",retry.deliveryId());
        assertThat(deliveries.recoverable()).contains(retry);
        deliveries.runClaimed(retry,() -> { deliveries.complete(retry.deliveryId(),"FAILED","PERMANENT","Safe summary"); deliveries.retry(retry.deliveryId(),false); });
        assertThat(deliveries.recoverable()).doesNotContain(retry);
    }
    @Test void recoveryScanIsBoundedAndInvalidLegacyMetadataTerminates() {
        try {
            jdbc.update("""
                    insert into github_webhook_deliveries(id,delivery_id,event_type,repository_full_name,external_run_id,run_attempt,status,next_attempt_at)
                    select gen_random_uuid(),'scan-bounded-'||n,'workflow_run','recovery/bulk',7799,1,'ACCEPTED',current_timestamp from generate_series(1,100) n
                    """);
            assertThat(deliveries.recoverable()).hasSizeLessThanOrEqualTo(10);
            deliveries.accept("legacy-missing-metadata","workflow_run","recovery/legacy",7798L,"ACCEPTED");
            deliveries.recoverable();
            assertThat(deliveries.status("legacy-missing-metadata")).contains("FAILED");
        } finally {jdbc.update("delete from github_webhook_deliveries where delivery_id like 'scan-bounded-%' or delivery_id='legacy-missing-metadata'");}
    }
    private void recoverThroughNewWorker() {
        var automation=org.mockito.Mockito.mock(com.axiom.application.webhook.GitHubWorkflowAutomationService.class);
        org.mockito.Mockito.doAnswer(call -> {
            WorkflowRunCommand command=call.getArgument(0);
            deliveries.runClaimed(command,() -> deliveries.complete(command.deliveryId(),"COMPLETED",null,null));return null;
        }).when(automation).process(org.mockito.ArgumentMatchers.any());
        new com.axiom.application.webhook.GitHubWebhookRecoveryWorker(deliveries,automation,
                new com.axiom.config.WebhookProperties(true,null,null,null,null),Runnable::run,
                new com.axiom.observability.AxiomMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())).recover();
    }
}
