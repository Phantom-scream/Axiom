package com.axiom.observability;

import com.axiom.domain.analysis.AnalysisStage;
import com.axiom.domain.analysis.AnalysisStageStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class AxiomMetrics {
    private final MeterRegistry registry;

    public AxiomMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void analysisCompleted(Timer.Sample sample, String outcome) {
        Counter.builder("axiom.analysis.runs")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        sample.stop(Timer.builder("axiom.analysis.duration")
                .tag("outcome", outcome)
                .register(registry));
    }

    public void analysisStage(
            AnalysisStage stage, AnalysisStageStatus status, Duration duration) {
        Timer.builder("axiom.analysis.stage.duration")
                .tag("stage", stage.name())
                .tag("status", status.name())
                .register(registry)
                .record(duration);
        if (status == AnalysisStageStatus.FAILED) {
            Counter.builder("axiom.analysis.stage.failures")
                    .tag("stage", stage.name())
                    .register(registry)
                    .increment();
        }
    }

    public void githubRequest(String operation, String outcome) {
        Counter.builder("axiom.github.requests")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void githubError(String operation, String category) {
        Counter.builder("axiom.github.errors")
                .tag("operation", operation)
                .tag("category", category)
                .register(registry)
                .increment();
    }

    public void githubRateLimit(String operation) {
        Counter.builder("axiom.github.rate_limits")
                .tag("operation", operation)
                .register(registry)
                .increment();
    }

    public void publication(String kind, String outcome) {
        String name = kind.equals("check")
                ? "axiom.github.check.publications"
                : "axiom.github.pr_comment.publications";
        Counter.builder(name).tag("outcome", outcome).register(registry).increment();
    }

    public void webhook(String outcome) {
        Counter.builder("axiom.webhook." + outcome).register(registry).increment();
    }

    public void webhookDuration(String outcome, Duration duration) {
        Timer.builder("axiom.webhook.processing.duration")
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    public void triageGenerated(String status) {
        Counter.builder("axiom.triage.generated")
                .tag("status", status)
                .register(registry)
                .increment();
    }
}
