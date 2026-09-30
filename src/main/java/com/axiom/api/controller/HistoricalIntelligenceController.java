package com.axiom.api.controller;

import com.axiom.api.dto.HistoricalIntelligenceResponse;
import com.axiom.api.dto.HistoricalIntelligenceResponse.*;
import com.axiom.application.history.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/repositories/{repositoryId}")
public class HistoricalIntelligenceController {
    private final FailureLifecycleService lifecycle;private final RecurringIncidentService incidents;private final RepositoryTrendService trends;
    private final TestReliabilityTrendService tests;private final RepositoryHotspotService hotspots;
    public HistoricalIntelligenceController(FailureLifecycleService lifecycle,RecurringIncidentService incidents,RepositoryTrendService trends,TestReliabilityTrendService tests,RepositoryHotspotService hotspots){this.lifecycle=lifecycle;this.incidents=incidents;this.trends=trends;this.tests=tests;this.hotspots=hotspots;}
    @GetMapping("/trends") public HistoricalIntelligenceResponse<Trend> trends(@PathVariable UUID repositoryId,@RequestParam(defaultValue="90") int days,@RequestParam(defaultValue="500") int maxRuns,@RequestParam(defaultValue="WEEK") Granularity granularity){return trends.get(repositoryId,days,maxRuns,granularity);}
    @GetMapping("/incidents") public HistoricalIntelligenceResponse<Incident> incidents(@PathVariable UUID repositoryId,@RequestParam(defaultValue="90") int days,@RequestParam(defaultValue="500") int maxRuns,@RequestParam(defaultValue="20") int limit){return incidents.get(repositoryId,days,maxRuns,limit);}
    @GetMapping("/failures/{fingerprint}/lifecycle") public HistoricalIntelligenceResponse<FailureLifecycle> lifecycle(@PathVariable UUID repositoryId,@PathVariable String fingerprint,@RequestParam(defaultValue="180") int days,@RequestParam(defaultValue="500") int maxRuns){return lifecycle.get(repositoryId,fingerprint,days,maxRuns);}
    @GetMapping("/tests/reliability") public HistoricalIntelligenceResponse<TestReliability> tests(@PathVariable UUID repositoryId,@RequestParam(defaultValue="90") int days,@RequestParam(defaultValue="500") int maxRuns,@RequestParam(defaultValue="20") int limit){return tests.get(repositoryId,days,maxRuns,limit);}
    @GetMapping("/hotspots") public HistoricalIntelligenceResponse<Hotspot> hotspots(@PathVariable UUID repositoryId,@RequestParam(defaultValue="90") int days,@RequestParam(defaultValue="500") int maxRuns,@RequestParam(defaultValue="20") int limit){return hotspots.get(repositoryId,days,maxRuns,limit);}
}
