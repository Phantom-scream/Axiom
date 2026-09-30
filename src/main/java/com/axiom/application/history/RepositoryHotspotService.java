package com.axiom.application.history;

import com.axiom.api.dto.HistoricalIntelligenceResponse;
import com.axiom.api.dto.HistoricalIntelligenceResponse.Hotspot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryHotspotService {
    private static final String MODULES="""
        , module_changes as (
          select distinct coalesce(nullif(cf.module_hint,''),'[category:'||cf.file_category||']') module,
            r.id run_id,r.conclusion,cf.id file_id
          from selected_runs r join git_change_sets cs on cs.pipeline_run_id=r.id join changed_files cf on cf.change_set_id=cs.id
        ), related as (
          select distinct m.module,cr.pipeline_run_id,cr.failure_event_id,cr.fingerprint,
            coalesce(s.classification,'UNKNOWN') classification
          from module_changes m join relevance_related_files rf on rf.changed_file_id=m.file_id
          join change_relevance_results cr on cr.id=rf.relevance_result_id
          left join signals s on s.id=cr.failure_event_id
          where cr.pipeline_run_id=m.run_id and cr.analyzer_version='change-relevance-v1' and cr.relevance in ('RELATED','LIKELY_RELATED')
        ), top_modules as (
          select module,count(distinct run_id) changed,count(distinct run_id) filter(where conclusion in ('FAILURE','TIMED_OUT')) failed,
            (select count(distinct (r.pipeline_run_id,r.fingerprint)) from related r where r.module=m.module) related_count
          from module_changes m group by module order by related_count desc,failed desc,changed desc,module limit ?
        )
        """;
    private final HistoricalQueryService history;
    public RepositoryHotspotService(HistoricalQueryService history){this.history=history;}
    @Transactional(readOnly=true)
    public HistoricalIntelligenceResponse<Hotspot> get(UUID repository,int days,int maxRuns,int limit) {
        return history.timed("hotspots",()->{
            var window=history.select(repository,days,maxRuns,limit);
            var modules=history.jdbc.query(HistoricalQueryService.RUNS+MODULES+"select * from top_modules order by related_count desc,failed desc,changed desc,module",
                (rs,row)->new Hotspot(rs.getString("module"),rs.getLong("changed"),rs.getLong("failed"),rs.getLong("related_count"),Map.of(),List.of()),window.arguments(limit));
            // Fixed query count: aggregate only selected top modules, never one lookup per module.
            var classifications=history.jdbc.query(HistoricalQueryService.RUNS+MODULES+"""
                select r.module,r.classification,count(distinct (r.pipeline_run_id,r.fingerprint)) amount
                from related r join top_modules t on t.module=r.module group by r.module,r.classification
                order by r.module,amount desc,r.classification
                """,(rs,row)->new Detail(rs.getString(1),rs.getString(2),rs.getLong(3)),window.arguments(limit));
            var fingerprints=history.jdbc.query(HistoricalQueryService.RUNS+MODULES+"""
                , ranked as (select r.module,r.fingerprint,count(distinct r.pipeline_run_id) amount,
                  row_number() over(partition by r.module order by count(distinct r.pipeline_run_id) desc,r.fingerprint) rank
                  from related r join top_modules t on t.module=r.module group by r.module,r.fingerprint)
                select module,fingerprint,amount from ranked where rank<=10 order by module,rank
                """,(rs,row)->new Detail(rs.getString(1),rs.getString(2),rs.getLong(3)),window.arguments(limit));
            Map<String,Map<String,Long>> counts=new LinkedHashMap<>(); classifications.forEach(row->counts.computeIfAbsent(row.module(),ignored->new LinkedHashMap<>()).put(row.key(),row.amount()));
            Map<String,List<String>> tops=new LinkedHashMap<>(); fingerprints.forEach(row->tops.computeIfAbsent(row.module(),ignored->new ArrayList<>()).add(row.key()));
            return window.response(modules.stream().map(row->new Hotspot(row.moduleHint(),row.changedRunCount(),row.failureAssociatedRunCount(),row.relatedFailureCount(),
                    counts.getOrDefault(row.moduleHint(),Map.of()),tops.getOrDefault(row.moduleHint(),List.of()))).toList());
        });
    }
    private record Detail(String module,String key,long amount) {}
}
