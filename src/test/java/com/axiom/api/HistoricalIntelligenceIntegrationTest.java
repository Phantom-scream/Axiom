package com.axiom.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.axiom.IntegrationTestSupport;
import com.axiom.api.dto.HistoricalIntelligenceResponse.*;
import com.axiom.application.history.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class HistoricalIntelligenceIntegrationTest extends IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private FailureLifecycleService lifecycle;
    @Autowired private RecurringIncidentService incidents;
    @Autowired private RepositoryTrendService trends;
    @Autowired private TestReliabilityTrendService tests;
    @Autowired private RepositoryHotspotService hotspots;
    @Autowired private io.micrometer.core.instrument.MeterRegistry meters;
    private UUID repository;
    private String flaky;
    @BeforeEach void seed() {
        repository=repository();
        UUID first=run(10,"FAILURE",100,1); UUID firstFailure=failure(first,"infra",3,"INFRASTRUCTURE_FAILURE");
        UUID pass=run(9,"SUCCESS",100,2);
        UUID second=run(8,"FAILURE",101,1);failure(second,"infra",2,"INFRASTRUCTURE_FAILURE");
        relevance(first,firstFailure,"UNRELATED",null);relevance(second,jdbc.queryForObject("select id from failure_events where pipeline_run_id=?",UUID.class,second),"UNLIKELY_RELATED",null);
        UUID dependency=run(7,"FAILURE",102,1);UUID dependencyFailure=failure(dependency,"dependency",1,"DEPENDENCY_FAILURE");
        relevance(dependency,dependencyFailure,"RELATED","payments");
        UUID test=run(6,"FAILURE",103,1);UUID testFailure=failure(test,"test",1,"TEST_FAILURE");
        UUID testPass=run(5,"SUCCESS",103,2);
        flaky=UUID.randomUUID().toString().replace("-","").repeat(2);
        execution(test,flaky,"FAILED",testFailure);execution(testPass,flaky,"PASSED",null);snapshot(flaky,"FLAKY");
        String suspected=UUID.randomUUID().toString().replace("-","").repeat(2);execution(first,suspected,"FAILED",firstFailure);snapshot(suspected,"SUSPECTED_FLAKY");
        String consistent=UUID.randomUUID().toString().replace("-","").repeat(2);execution(dependency,consistent,"FAILED",dependencyFailure);snapshot(consistent,"CONSISTENTLY_FAILING");
        run(4,"CANCELLED",104,1);
        failure(run(3,"FAILURE",105,1),"active",1,"DEPENDENCY_FAILURE");
        UUID latest=run(1,"FAILURE",106,1);failure(latest,"active",1,"DEPENDENCY_FAILURE");
        jdbc.update("insert into pipeline_triage_results(id,pipeline_run_id,rerun_recommendation,rerun_confidence,summary,triage_version) values(?,?,'RECOMMENDED',0.8,'fixture','triage-v1')",UUID.randomUUID(),first);
    }
    @Test void lifecycleAndIncidentsUseCrossRunEvidenceAndNeverMergeFingerprints() {
        var infra=lifecycle.get(repository,"infra",90,500).entries().getFirst();
        assertThat(infra.occurrenceCount()).isEqualTo(5);assertThat(infra.affectedRunCount()).isEqualTo(2);
        assertThat(infra.firstSeenAt()).isBefore(infra.lastSeenAt());assertThat(infra.currentStatus()).isEqualTo(LifecycleStatus.INTERMITTENT);
        assertThat(lifecycle.get(repository,"active",90,500).entries().getFirst().currentStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(lifecycle.get(repository,"dependency",90,500).entries().getFirst().currentStatus()).isEqualTo(LifecycleStatus.UNKNOWN);
        var grouped=incidents.get(repository,90,500,20).entries();
        assertThat(grouped).extracting(Incident::fingerprint).containsExactly("infra","active","test","dependency");
        assertThat(grouped.getFirst().rerunRecoveryCount()).isEqualTo(1);
        assertThat(grouped.getFirst().changeUnrelatedCount()).isEqualTo(2);
        assertThat(grouped.stream().filter(i->i.fingerprint().equals("dependency")).findFirst().orElseThrow().changeRelatedCount()).isEqualTo(1);
        assertThat(incidents.get(repository,90,500,1).entries()).hasSize(1);
    }
    @Test void resolvedRequiresObservedCleanRunsNotMereAbsence() {
        repository=repository();failure(run(20,"FAILURE",201,1),"resolved",1,"TEST_FAILURE");failure(run(19,"FAILURE",202,1),"resolved",1,"TEST_FAILURE");
        run(18,"SUCCESS",203,1);run(17,"SUCCESS",204,1);run(16,"SUCCESS",205,1);
        assertThat(lifecycle.get(repository,"resolved",90,500).entries().getFirst().currentStatus()).isEqualTo(LifecycleStatus.RESOLVED);
        assertThat(lifecycle.get(repository,"absent",90,500).entries().getFirst().currentStatus()).isEqualTo(LifecycleStatus.UNKNOWN);
    }
    @Test void dailyAndWeeklyTrendsPreserveCountsAndRateDenominators() {
        var day=trends.get(repository,30,500,Granularity.DAY);
        assertThat(day.entries()).hasSize(31);assertThat(day.entries().stream().mapToLong(Trend::totalRuns).sum()).isEqualTo(9);
        assertThat(day.entries().stream().mapToLong(Trend::successfulRuns).sum()).isEqualTo(2);
        assertThat(day.entries().stream().mapToLong(Trend::cancelledRuns).sum()).isEqualTo(1);
        assertThat(day.entries().stream().mapToLong(Trend::newFailureFingerprints).sum()).isEqualTo(4);
        assertThat(day.entries().stream().mapToLong(Trend::recurringFailureFingerprints).sum()).isEqualTo(2);
        assertThat(day.entries().stream().mapToLong(Trend::infrastructureFailureCount).sum()).isEqualTo(2);
        assertThat(day.entries().stream().mapToLong(Trend::rerunRecommendedCount).sum()).isEqualTo(1);
        var week=trends.get(repository,30,500,Granularity.WEEK);
        assertThat(week.entries().stream().mapToLong(Trend::totalRuns).sum()).isEqualTo(9);
        assertThat(week.entries()).allMatch(bucket->bucket.successRate()>=0 && bucket.successRate()<=1 && bucket.bucketStart().getDayOfWeek()==java.time.DayOfWeek.MONDAY);
    }
    @Test void testReliabilityUsesSnapshotsAndExistingRerunService() {
        var rows=tests.get(repository,90,500,20).entries();assertThat(rows).hasSize(3);
        assertThat(rows).extracting(TestReliability::stabilityClass).containsExactlyInAnyOrder("FLAKY","SUSPECTED_FLAKY","CONSISTENTLY_FAILING");
        var row=rows.stream().filter(t->t.stableTestId().equals(flaky)).findFirst().orElseThrow();
        assertThat(row.executionCount()).isEqualTo(2);assertThat(row.passCount()).isEqualTo(1);assertThat(row.failureCount()).isEqualTo(1);assertThat(row.recentFailToPassRerunCount()).isEqualTo(1);
        assertThat(tests.get(repository,90,500,1).entries()).hasSize(1);
    }
    @Test void hotspotsAggregatePersistedAssociations() {
        var row=hotspots.get(repository,90,500,20).entries().getFirst();
        assertThat(row.moduleHint()).isEqualTo("payments");assertThat(row.changedRunCount()).isEqualTo(1);
        assertThat(row.failureAssociatedRunCount()).isEqualTo(1);assertThat(row.relatedFailureCount()).isEqualTo(1);
        assertThat(row.topFailureClassifications()).containsEntry("DEPENDENCY_FAILURE",1L);
        assertThat(row.topFingerprints()).containsExactly("dependency");
    }
    @Test void historicalMetricsAreOperationalWithoutRepositoryOrFingerprintTags() {
        incidents.get(repository,90,500,20);trends.get(repository,90,500,Granularity.DAY);
        assertThat(meters.get("axiom.history.query.duration").tag("operation","incidents").timer().count()).isPositive();
        assertThat(meters.get("axiom.history.incidents.returned").counter().count()).isPositive();
        assertThat(meters.find("axiom.history.query.duration").timers()).allSatisfy(timer ->
            assertThat(timer.getId().getTags()).allMatch(tag -> tag.getKey().equals("operation") || tag.getKey().equals("application")));
    }
    @Test void boundedWindowsExcludeOldRunsAndLimitRunCount() {
        assertThat(incidents.get(repository,2,500,20).window().selectedRuns()).isEqualTo(1);
        assertThat(trends.get(repository,90,2,Granularity.DAY).entries().stream().mapToLong(Trend::totalRuns).sum()).isEqualTo(2);
        assertThat(incidents.get(repository,90,2,20).entries()).extracting(Incident::fingerprint).containsExactly("active");
    }
    @ParameterizedTest @ValueSource(strings={"trends","incidents","failures/infra/lifecycle","tests/reliability","hotspots"})
    void endpointsReturnPersistedDataAndRejectMissingRepository(String endpoint) throws Exception {
        mvc.perform(get("/api/v1/repositories/"+repository+"/"+endpoint)).andExpect(status().isOk()).andExpect(jsonPath("$.window.selectedRuns").value(9));
        mvc.perform(get("/api/v1/repositories/"+UUID.randomUUID()+"/"+endpoint)).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"trends?days=0","trends?granularity=MONTH","trends?maxRuns=1001","incidents?limit=0","hotspots?days=-1","tests/reliability?limit=101"})
    void invalidBoundsAreRejected(String endpoint) throws Exception {
        mvc.perform(get("/api/v1/repositories/"+repository+"/"+endpoint)).andExpect(status().isBadRequest());
    }
    @Test void emptyRepositoryReturnsValidZeroStateForEveryView() {
        repository=repository();assertThat(incidents.get(repository,90,500,20).entries()).isEmpty();
        assertThat(hotspots.get(repository,90,500,20).entries()).isEmpty();assertThat(tests.get(repository,90,500,20).entries()).isEmpty();
        assertThat(trends.get(repository,90,500,Granularity.DAY).entries()).allMatch(row->row.totalRuns()==0 && row.successRate()==0);
    }
    @Test void hundredsOfRunsAndSignalsRemainBoundedWithoutTimingAssumptions() {
        jdbc.update("""
                insert into pipeline_runs(id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,finished_at)
                select gen_random_uuid(),?,10000+n,'perf','COMPLETED','FAILURE',1,current_timestamp-n*interval '1 minute'
                from generate_series(1,600) n
                """,repository);
        jdbc.update("""
                insert into failure_events(id,pipeline_run_id,event_type,normalized_message,fingerprint,fingerprint_algorithm,occurrence_count)
                select gen_random_uuid(),id,'UNKNOWN','performance fixture','perf-'||(external_run_id%5),'v1',1
                from pipeline_runs where repository_id=? and external_run_id>=10000
                """,repository);
        var incidentsResult=incidents.get(repository,90,100,3);
        assertThat(incidentsResult.window().selectedRuns()).isEqualTo(100);
        assertThat(incidentsResult.entries()).hasSize(3);
        assertThat(incidentsResult.entries()).allMatch(row->row.affectedRunCount()==20);
        assertThat(trends.get(repository,90,100,Granularity.DAY).entries().stream().mapToLong(Trend::totalRuns).sum()).isEqualTo(100);
        assertThat(tests.get(repository,90,100,20).entries()).isEmpty();
    }
    private UUID repository() { UUID id=UUID.randomUUID();jdbc.update("insert into repositories(id,provider,owner,name) values(?,'GITHUB_ACTIONS','history-fixture',?)",id,id.toString());return id; }
    private UUID run(int days,String conclusion,long external,int attempt) {
        UUID id=UUID.randomUUID();jdbc.update("insert into pipeline_runs(id,repository_id,external_run_id,commit_sha,status,conclusion,attempt,finished_at,branch) values(?,?,?,'same-sha','COMPLETED',?,?,?,'feature')",id,repository,external,conclusion,attempt,Timestamp.from(Instant.now().minus(days,ChronoUnit.DAYS)));return id;
    }
    private UUID failure(UUID run,String fingerprint,int occurrences,String classification) {
        UUID id=UUID.randomUUID();jdbc.update("insert into failure_events(id,pipeline_run_id,event_type,normalized_message,fingerprint,fingerprint_algorithm,occurrence_count) values(?,?,'UNKNOWN','fixture',?,'v1',?)",id,run,fingerprint,occurrences);
        jdbc.update("insert into failure_diagnoses(id,pipeline_run_id,failure_event_id,fingerprint,classification,confidence,confidence_level,summary,classifier_version) values(?,?,?,?,?,0.8,'HIGH','fixture','axiom-classifier-v1')",UUID.randomUUID(),run,id,fingerprint,classification);return id;
    }
    private void relevance(UUID run,UUID failure,String relevance,String module) {
        UUID result=UUID.randomUUID();String fingerprint=jdbc.queryForObject("select fingerprint from failure_events where id=?",String.class,failure);
        jdbc.update("insert into change_relevance_results(id,pipeline_run_id,failure_event_id,fingerprint,relevance,confidence,analyzer_version,summary) values(?,?,?,?,?,0.8,'change-relevance-v1','fixture')",result,run,failure,fingerprint,relevance);
        if(module!=null) {UUID changes=UUID.randomUUID(),file=UUID.randomUUID();
            jdbc.update("insert into git_change_sets(id,pipeline_run_id,base_sha,head_sha,total_changed_files,provider) values(?,?,'base','head',1,'GITHUB')",changes,run);
            jdbc.update("insert into changed_files(id,change_set_id,path,change_type,file_category,module_hint) values(?,?,'payments/pom.xml','MODIFIED','DEPENDENCY_MANIFEST',?)",file,changes,module);
            jdbc.update("insert into relevance_related_files(relevance_result_id,changed_file_id,relationship,weight) values(?,?,'DEPENDENCY',0.8)",result,file);
        }
    }
    private void execution(UUID run,String stable,String state,UUID failure) {
        UUID report=UUID.randomUUID(),suite=UUID.randomUUID();
        jdbc.update("insert into test_reports(id,pipeline_run_id,source_name,content_sha256,total_tests,passed,failed,errors,skipped) values(?,?,'fixture.xml',?,1,0,1,0,0)",report,run,stable);
        jdbc.update("insert into test_suites(id,test_report_id,name) values(?,?,'fixture')",suite,report);
        String fingerprint=failure==null?null:jdbc.queryForObject("select fingerprint from failure_events where id=?",String.class,failure);
        jdbc.update("insert into test_case_executions(id,test_suite_id,pipeline_run_id,stable_test_id,test_id_algorithm,test_name,status,failure_fingerprint,correlated_failure_event_id,correlation_strength) values(?,?,?,?,'test-id-v1','fixture',?,?,?,?)",UUID.randomUUID(),suite,run,stable,state,fingerprint,failure,failure==null?"NONE":"EXACT");
    }
    private void snapshot(String stable,String classification) {
        jdbc.update("insert into test_stability_snapshots(id,stable_test_id,stability_class,classifier_version,total_executions,pass_count,failure_count,error_count,fail_to_pass_reruns,unique_failure_fingerprints) values(?,?,?,'stability-v1',5,1,4,0,1,1)",UUID.randomUUID(),stable,classification);
    }
}
