package com.axiom.application.history;

import com.axiom.api.dto.HistoricalIntelligenceResponse;
import com.axiom.api.dto.HistoricalIntelligenceResponse.TestReliability;
import com.axiom.application.testreport.RerunAnalysisService;
import com.axiom.domain.test.RerunTransitionType;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestReliabilityTrendService {
    private final HistoricalQueryService history;
    private final RerunAnalysisService reruns;
    public TestReliabilityTrendService(HistoricalQueryService history,RerunAnalysisService reruns){this.history=history;this.reruns=reruns;}
    @Transactional(readOnly=true)
    public HistoricalIntelligenceResponse<TestReliability> get(UUID repository,int days,int maxRuns,int limit) {
        return history.timed("test_reliability",() -> {
            var window=history.select(repository,days,maxRuns,limit);
            var rows=history.jdbc.query(HistoricalQueryService.RUNS+"""
                select trim(t.stable_test_id) stable_id,s.stability_class,count(*) executions,
                  count(*) filter(where t.status in ('FAILED','ERROR')) failures,
                  count(*) filter(where t.status='PASSED') passes,
                  min(r.occurred_at) filter(where t.status in ('FAILED','ERROR')) first_failure,
                  max(r.occurred_at) filter(where t.status in ('FAILED','ERROR')) last_failure
                from test_case_executions t join selected_runs r on r.id=t.pipeline_run_id
                join test_stability_snapshots s on s.stable_test_id=t.stable_test_id and s.classifier_version='stability-v1'
                where s.stability_class in ('FLAKY','SUSPECTED_FLAKY','CONSISTENTLY_FAILING')
                group by t.stable_test_id,s.stability_class
                order by count(*) filter(where t.status in ('FAILED','ERROR')) desc,stable_id limit ?
                """,(rs,row)->new TestReliability(rs.getString("stable_id"),rs.getString("stability_class"),rs.getLong("executions"),rs.getLong("failures"),rs.getLong("passes"),0,
                    HistoricalQueryService.instant(rs,"first_failure"),HistoricalQueryService.instant(rs,"last_failure")),window.arguments(limit));
            var runIds=history.jdbc.query(HistoricalQueryService.RUNS+"select id from selected_runs",(rs,row)->rs.getObject(1,UUID.class),window.arguments());
            var transitions=reruns.transitionsForRuns(rows.stream().map(TestReliability::stableTestId).collect(Collectors.toSet()),runIds);
            return window.response(rows.stream().map(row -> new TestReliability(row.stableTestId(),row.stabilityClass(),row.executionCount(),row.failureCount(),row.passCount(),
                    transitions.getOrDefault(row.stableTestId(),java.util.List.of()).stream().filter(t->t.sameCommitSha() && (t.transitionType()==RerunTransitionType.FAIL_TO_PASS || t.transitionType()==RerunTransitionType.ERROR_TO_PASS)).count(),
                    row.firstFailureSeen(),row.lastFailureSeen())).toList());
        });
    }
}
