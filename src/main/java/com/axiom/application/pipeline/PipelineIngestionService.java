package com.axiom.application.pipeline;
import com.axiom.domain.pipeline.CiProviderType;
import com.axiom.domain.pipeline.PipelineRun;
import com.axiom.integrations.ci.CiProvider;
import com.axiom.logstorage.LogStorage;
import java.util.List;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service public class PipelineIngestionService {
    private static final Logger log=LoggerFactory.getLogger(PipelineIngestionService.class); private final List<CiProvider> providers; private final PipelinePersistenceService persistence; private final LogStorage logs;
    public PipelineIngestionService(List<CiProvider> providers, PipelinePersistenceService persistence, LogStorage logs) {this.providers=providers;this.persistence=persistence;this.logs=logs;}
    public PersistedPipelineRun ingest(PipelineRunReference ref) { CiProvider provider=providers.stream().filter(p->p.providerType()==ref.provider()).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported CI provider: "+ref.provider())); log.info("pipeline_ingestion_started provider={} repository={}/{} externalRunId={}",ref.provider(),ref.repositoryOwner(),ref.repositoryName(),ref.externalRunId()); PipelineRun run=provider.fetchRun(ref.repositoryOwner(),ref.repositoryName(),ref.externalRunId()); PersistedPipelineRun saved=persistence.save(run); logs.store(saved.id(),provider.downloadRunLogs(ref.repositoryOwner(),ref.repositoryName(),ref.externalRunId())); log.info("pipeline_ingestion_completed provider={} externalRunId={} runAttempt={} jobCount={}",ref.provider(),ref.externalRunId(),saved.attempt(),saved.jobCount()); return saved; }
}
