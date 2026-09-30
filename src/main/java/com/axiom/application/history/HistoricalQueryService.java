package com.axiom.application.history;

import com.axiom.api.dto.HistoricalIntelligenceResponse;
import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.config.HistoricalIntelligenceProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class HistoricalQueryService {
    static final String RUNS = """
        with selected_runs as materialized (
          select p.*,coalesce(finished_at,started_at,ingested_at,created_at) occurred_at
          from pipeline_runs p where repository_id=?
            and coalesce(finished_at,started_at,ingested_at,created_at)>=?
            and coalesce(finished_at,started_at,ingested_at,created_at)<=?
          order by coalesce(finished_at,started_at,ingested_at,created_at) desc,id limit ?
        ), ordered_runs as (
          select *,row_number() over(order by occurred_at desc,id) recent_rank from selected_runs
        ), signals as materialized (
          select fe.*,r.occurred_at,r.branch,r.external_run_id,r.attempt,r.commit_sha,r.recent_rank,r.conclusion run_conclusion,
                 coalesce(d.classification,'UNKNOWN') classification
          from failure_events fe join ordered_runs r on r.id=fe.pipeline_run_id
          left join lateral (select classification from failure_diagnoses
            where failure_event_id=fe.id and classifier_version='axiom-classifier-v1'
            order by created_at desc,id limit 1) d on true
        )
        """;
    final JdbcTemplate jdbc;
    final Clock clock;
    final HistoricalIntelligenceProperties properties;
    private final MeterRegistry meters;
    public HistoricalQueryService(JdbcTemplate jdbc, Clock clock, HistoricalIntelligenceProperties properties, MeterRegistry meters) {
        this.jdbc=jdbc;this.clock=clock;this.properties=properties;this.meters=meters;
    }
    Window select(UUID repository,int days,int maxRuns,int limit) {
        if(days<1 || days>properties.days()) throw new IllegalArgumentException("days must be between 1 and " + properties.days() + ".");
        if(maxRuns<1 || maxRuns>properties.runs()) throw new IllegalArgumentException("maxRuns must be between 1 and " + properties.runs() + ".");
        if(limit<1 || limit>properties.results()) throw new IllegalArgumentException("limit must be between 1 and " + properties.results() + ".");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from repositories where id=?)",Boolean.class,repository)))
            throw new ResourceNotFoundException("Repository " + repository + " was not found.");
        Instant now=clock.instant();
        var window=new Window(repository,days,maxRuns,limit,Timestamp.from(now.minus(days,ChronoUnit.DAYS)),Timestamp.from(now));
        int count=jdbc.queryForObject(RUNS+"select count(*) from selected_runs",Integer.class,window.arguments());
        return new Window(repository,days,maxRuns,limit,window.cutoff,window.now,count);
    }
    <T> T timed(String operation,Supplier<T> query) {
        var sample=Timer.start(meters);
        try { return query.get(); } finally { sample.stop(Timer.builder("axiom.history.query.duration").tag("operation",operation).register(meters)); }
    }
    void incidentsReturned(int count) { meters.counter("axiom.history.incidents.returned").increment(count); }
    record Window(UUID repository,int days,int maxRuns,int limit,Timestamp cutoff,Timestamp now,int count) {
        Window(UUID repository,int days,int maxRuns,int limit,Timestamp cutoff,Timestamp now) {this(repository,days,maxRuns,limit,cutoff,now,0);}
        Object[] arguments(Object... extra) {
            Object[] values=new Object[4+extra.length]; values[0]=repository;values[1]=cutoff;values[2]=now;values[3]=maxRuns;
            System.arraycopy(extra,0,values,4,extra.length); return values;
        }
        <T> HistoricalIntelligenceResponse<T> response(List<T> rows) {return new HistoricalIntelligenceResponse<>(repository,new HistoricalIntelligenceResponse.Window(days,maxRuns,count),rows);}
    }
    static Instant instant(java.sql.ResultSet rs,String column) throws java.sql.SQLException {var value=rs.getTimestamp(column);return value==null?null:value.toInstant();}
}
