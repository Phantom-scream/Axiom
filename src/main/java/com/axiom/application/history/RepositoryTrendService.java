package com.axiom.application.history;

import com.axiom.api.dto.HistoricalIntelligenceResponse;
import com.axiom.api.dto.HistoricalIntelligenceResponse.Granularity;
import com.axiom.api.dto.HistoricalIntelligenceResponse.Trend;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryTrendService {
    private final HistoricalQueryService history;
    public RepositoryTrendService(HistoricalQueryService history) {this.history=history;}
    @Transactional(readOnly=true)
    public HistoricalIntelligenceResponse<Trend> get(UUID repository,int days,int maxRuns,Granularity granularity) {
        return history.timed("trends",() -> {
            var window=history.select(repository,days,maxRuns,1);
            String unit=granularity==Granularity.DAY?"day":"week";
            var rows=history.jdbc.query(HistoricalQueryService.RUNS+"""
                , bucket_runs as (select *,date_trunc(?,occurred_at at time zone 'UTC')::date bucket from selected_runs),
                fingerprint_first as (select fingerprint,min(occurred_at) first_seen from signals group by fingerprint),
                bucket_signals as (select s.*,date_trunc(?,s.occurred_at at time zone 'UTC')::date bucket,f.first_seen from signals s join fingerprint_first f using(fingerprint))
                select b.bucket,count(*) total,count(*) filter(where conclusion='SUCCESS') successful,
                  count(*) filter(where conclusion in ('FAILURE','TIMED_OUT')) failed,count(*) filter(where conclusion='CANCELLED') cancelled,
                  (select count(distinct fingerprint) from bucket_signals s where s.bucket=b.bucket and date_trunc(?,first_seen at time zone 'UTC')::date=b.bucket) new_fingerprints,
                  (select count(*) from (select fingerprint from bucket_signals s where s.bucket=b.bucket group by fingerprint
                    having min(first_seen)<min(occurred_at) or count(distinct pipeline_run_id)>1) x) recurring_fingerprints,
                  (select count(*) from pipeline_triage_results t join bucket_runs r on r.id=t.pipeline_run_id
                    where r.bucket=b.bucket and t.triage_version='triage-v1' and t.rerun_recommendation='RECOMMENDED') rerun,
                  (select count(distinct (cr.pipeline_run_id,cr.fingerprint)) from change_relevance_results cr join bucket_runs r on r.id=cr.pipeline_run_id
                    where r.bucket=b.bucket and cr.analyzer_version='change-relevance-v1' and cr.relevance in ('RELATED','LIKELY_RELATED')) related,
                  (select count(distinct (s.pipeline_run_id,s.fingerprint)) from bucket_signals s where s.bucket=b.bucket and classification='INFRASTRUCTURE_FAILURE') infrastructure,
                  (select count(distinct (s.pipeline_run_id,s.fingerprint)) from bucket_signals s where s.bucket=b.bucket and classification='TEST_FAILURE') tests,
                  (select count(distinct (s.pipeline_run_id,s.fingerprint)) from bucket_signals s where s.bucket=b.bucket and classification='DEPENDENCY_FAILURE') dependency
                from bucket_runs b group by b.bucket order by b.bucket
                """,(rs,row)->{
                    long successful=rs.getLong("successful"),failed=rs.getLong("failed");
                    return new Trend(rs.getObject("bucket",LocalDate.class),granularity,rs.getLong("total"),successful,failed,rs.getLong("cancelled"),
                        successful+failed==0?0:successful/(double)(successful+failed),rs.getLong("new_fingerprints"),rs.getLong("recurring_fingerprints"),
                        rs.getLong("rerun"),rs.getLong("related"),rs.getLong("infrastructure"),rs.getLong("tests"),rs.getLong("dependency"));
                },window.arguments(unit,unit,unit));
            var byDate=new HashMap<LocalDate,Trend>();rows.forEach(row->byDate.put(row.bucketStart(),row));
            var start=window.cutoff().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            var end=window.now().toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            if(granularity==Granularity.WEEK) start=start.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            var output=new ArrayList<Trend>();
            for(var date=start;!date.isAfter(end);date=date.plusDays(granularity==Granularity.DAY?1:7))
                output.add(byDate.getOrDefault(date,new Trend(date,granularity,0,0,0,0,0,0,0,0,0,0,0,0)));
            return window.response(output);
        });
    }
}
