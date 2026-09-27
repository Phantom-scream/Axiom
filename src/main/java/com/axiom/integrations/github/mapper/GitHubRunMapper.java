package com.axiom.integrations.github.mapper;

import com.axiom.domain.pipeline.*;
import com.axiom.integrations.github.dto.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GitHubRunMapper {
    public PipelineRun run(GitHubWorkflowRunDto source, String owner, String repository, List<PipelineJob> jobs) {
        Long pr = source.pullRequests() == null || source.pullRequests().isEmpty() ? null : source.pullRequests().getFirst().number();
        return new PipelineRun(UUID.randomUUID(), CiProviderType.GITHUB_ACTIONS, source.id(), owner, repository, source.headSha(), source.headBranch(), pr, status(source.status()), conclusion(source.conclusion()), Math.max(1, source.runAttempt()), source.createdAt(), source.updatedAt(), jobs);
    }
    public PipelineJob job(GitHubJobDto source) {
        List<PipelineStep> steps = source.steps() == null ? List.of() : source.steps().stream().map(this::step).toList();
        return new PipelineJob(UUID.randomUUID(), source.id(), source.name(), status(source.status()), conclusion(source.conclusion()), source.runnerName(), source.startedAt(), source.completedAt(), steps);
    }
    private PipelineStep step(GitHubStepDto source) { return new PipelineStep(source.number(), source.name(), status(source.status()), conclusion(source.conclusion()), source.startedAt(), source.completedAt()); }
    private PipelineStatus status(String status) { if ("queued".equalsIgnoreCase(status)) return PipelineStatus.QUEUED; if ("in_progress".equalsIgnoreCase(status) || "running".equalsIgnoreCase(status)) return PipelineStatus.RUNNING; return PipelineStatus.COMPLETED; }
    private PipelineConclusion conclusion(String conclusion) { if (conclusion == null) return PipelineConclusion.UNKNOWN; try { return PipelineConclusion.valueOf(conclusion.toUpperCase().replace('-', '_')); } catch (IllegalArgumentException ignored) { return PipelineConclusion.UNKNOWN; } }
}
