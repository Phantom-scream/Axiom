package com.axiom.application.history;

import com.axiom.api.dto.HistoricalIntelligenceResponse;
import com.axiom.api.dto.HistoricalIntelligenceResponse.Incident;
import com.axiom.api.dto.HistoricalIntelligenceResponse.LifecycleStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringIncidentService {
    private final HistoricalQueryService history;
    public RecurringIncidentService(HistoricalQueryService history) {this.history=history;}
    @Transactional(readOnly=true)
    public HistoricalIntelligenceResponse<Incident> get(UUID repository,int days,int maxRuns,int limit) {
        return history.timed("incidents",() -> {var window=history.select(repository,days,maxRuns,limit);var rows=rows(window,null);history.incidentsReturned(rows.size());return window.response(rows);});
    }
    List<Incident> rows(HistoricalQueryService.Window window,String fingerprint) {
        return history.jdbc.query(HistoricalQueryService.RUNS+"""
            , grouped as (
              select fingerprint,sum(occurrence_count) occurrences,count(distinct pipeline_run_id) affected,
                     min(occurred_at) first_seen,max(occurred_at) last_seen,
                     coalesce(min(recent_rank) filter(where run_conclusion in ('FAILURE','TIMED_OUT')),2147483647) recent_rank,
                     (array_agg(classification order by occurred_at desc,id))[1] classification
              from signals where (?::varchar is null or fingerprint=?) group by fingerprint
            )
            select g.*,
              (select count(distinct s.pipeline_run_id) from signals s
                join lateral (select n.conclusion,n.commit_sha from selected_runs n
                  where n.external_run_id=s.external_run_id and n.attempt>s.attempt
                  order by n.attempt limit 1) n on n.conclusion='SUCCESS' and n.commit_sha=s.commit_sha
                where s.fingerprint=g.fingerprint and s.commit_sha is not null) recoveries,
              (select count(*) from selected_runs r where r.occurred_at>g.last_seen and r.status='COMPLETED'
                and (r.conclusion='SUCCESS' or (exists(select 1 from pipeline_triage_results t where t.pipeline_run_id=r.id)
                  and not exists(select 1 from signals s where s.pipeline_run_id=r.id and s.fingerprint=g.fingerprint)))) clean_after,
              exists(select 1 from selected_runs r where r.conclusion='SUCCESS' and r.occurred_at>g.first_seen and r.occurred_at<g.last_seen) clean_between,
              (select trim(t.stable_test_id) from test_case_executions t join signals s on s.id=t.correlated_failure_event_id
                where s.fingerprint=g.fingerprint and t.correlation_strength in ('EXACT','STRONG')
                group by t.stable_test_id order by count(*) desc,t.stable_test_id limit 1) common_test,
              (select cf.file_category from relevance_related_files rf join changed_files cf on cf.id=rf.changed_file_id
                join change_relevance_results cr on cr.id=rf.relevance_result_id join selected_runs r on r.id=cr.pipeline_run_id
                where cr.fingerprint=g.fingerprint and cr.analyzer_version='change-relevance-v1'
                group by cf.file_category order by count(*) desc,cf.file_category limit 1) common_category,
              (select cf.module_hint from relevance_related_files rf join changed_files cf on cf.id=rf.changed_file_id
                join change_relevance_results cr on cr.id=rf.relevance_result_id join selected_runs r on r.id=cr.pipeline_run_id
                where cr.fingerprint=g.fingerprint and cr.analyzer_version='change-relevance-v1' and cf.module_hint is not null
                group by cf.module_hint order by count(*) desc,cf.module_hint limit 1) common_module,
              (select count(distinct cr.pipeline_run_id) from change_relevance_results cr join selected_runs r on r.id=cr.pipeline_run_id
                where cr.fingerprint=g.fingerprint and cr.analyzer_version='change-relevance-v1' and cr.relevance in ('RELATED','LIKELY_RELATED')) related,
              (select count(distinct cr.pipeline_run_id) from change_relevance_results cr join selected_runs r on r.id=cr.pipeline_run_id
                where cr.fingerprint=g.fingerprint and cr.analyzer_version='change-relevance-v1' and cr.relevance in ('UNRELATED','UNLIKELY_RELATED')) unrelated
            from grouped g order by occurrences desc,last_seen desc,fingerprint limit ?
            """,(rs,row) -> new Incident(rs.getString("fingerprint"),rs.getString("classification"),rs.getLong("occurrences"),rs.getLong("affected"),
                HistoricalQueryService.instant(rs,"first_seen"),HistoricalQueryService.instant(rs,"last_seen"),
                status(rs.getLong("affected"),rs.getLong("recoveries"),rs.getBoolean("clean_between"),rs.getInt("clean_after"),rs.getInt("recent_rank")),
                rs.getString("common_test"),rs.getString("common_category"),rs.getString("common_module"),rs.getLong("recoveries"),rs.getLong("related"),rs.getLong("unrelated")),
                window.arguments(fingerprint,fingerprint,window.limit()));
    }
    LifecycleStatus status(long affected,long recoveries,boolean cleanBetween,int cleanAfter,int recentRank) {
        if(affected>=history.properties.recurring() && cleanAfter>=history.properties.resolved()) return LifecycleStatus.RESOLVED;
        if(recoveries>0 || (affected>=history.properties.recurring() && cleanBetween)) return LifecycleStatus.INTERMITTENT;
        if(affected>=history.properties.recurring() && recentRank<=history.properties.active()) return LifecycleStatus.ACTIVE;
        return LifecycleStatus.UNKNOWN;
    }
}
