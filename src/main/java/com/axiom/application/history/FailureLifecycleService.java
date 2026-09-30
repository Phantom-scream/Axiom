package com.axiom.application.history;

import com.axiom.api.dto.HistoricalIntelligenceResponse;
import com.axiom.api.dto.HistoricalIntelligenceResponse.FailureLifecycle;
import com.axiom.api.dto.HistoricalIntelligenceResponse.LifecycleStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailureLifecycleService {
    private final HistoricalQueryService history;
    private final RecurringIncidentService incidents;
    public FailureLifecycleService(HistoricalQueryService history,RecurringIncidentService incidents) {this.history=history;this.incidents=incidents;}
    @Transactional(readOnly=true)
    public HistoricalIntelligenceResponse<FailureLifecycle> get(UUID repository,String fingerprint,int days,int maxRuns) {
        if(fingerprint==null || fingerprint.isBlank() || fingerprint.length()>64) throw new IllegalArgumentException("fingerprint must contain 1 to 64 characters.");
        return history.timed("lifecycle",() -> {
            var window=history.select(repository,days,maxRuns,1);var rows=incidents.rows(window,fingerprint);
            if(rows.isEmpty()) return window.response(List.of(new FailureLifecycle(fingerprint,null,null,0,0,"UNKNOWN",List.of(),LifecycleStatus.UNKNOWN)));
            var incident=rows.getFirst();
            var branches=history.jdbc.query(HistoricalQueryService.RUNS+"select distinct branch from signals where fingerprint=? and branch is not null order by branch limit 20",
                    (rs,row)->rs.getString(1),window.arguments(fingerprint));
            return window.response(List.of(new FailureLifecycle(fingerprint,incident.firstSeenAt(),incident.lastSeenAt(),incident.occurrenceCount(),incident.affectedRunCount(),incident.classification(),branches,incident.lifecycleStatus())));
        });
    }
}
