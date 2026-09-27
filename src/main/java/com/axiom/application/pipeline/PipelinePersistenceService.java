package com.axiom.application.pipeline;
import com.axiom.domain.pipeline.*;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class PipelinePersistenceService {
    private final JdbcTemplate jdbc; private final Clock clock;
    public PipelinePersistenceService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }
    @Transactional public PersistedPipelineRun save(PipelineRun run) {
        Instant now=clock.instant(); UUID repositoryId=findOrCreateRepository(run,now); UUID id=findRun(repositoryId,run.externalRunId(),run.attempt());
        if(id==null) { id=UUID.randomUUID(); jdbc.update("insert into pipeline_runs(id,repository_id,external_run_id,commit_sha,branch,pull_request_number,status,conclusion,attempt,started_at,finished_at,ingested_at) values (?,?,?,?,?,?,?,?,?,?,?,?)",id,repositoryId,run.externalRunId(),run.commitSha(),run.branch(),run.pullRequestNumber(),run.status().name(),run.conclusion().name(),run.attempt(),run.startedAt(),run.finishedAt(),now); }
        else jdbc.update("update pipeline_runs set commit_sha=?,branch=?,pull_request_number=?,status=?,conclusion=?,started_at=?,finished_at=?,ingested_at=? where id=?",run.commitSha(),run.branch(),run.pullRequestNumber(),run.status().name(),run.conclusion().name(),run.startedAt(),run.finishedAt(),now,id);
        jdbc.update("delete from pipeline_jobs where pipeline_run_id=?",id);
        int steps=0; for(PipelineJob job:run.jobs()) { UUID jobId=UUID.randomUUID(); jdbc.update("insert into pipeline_jobs(id,pipeline_run_id,external_job_id,name,status,conclusion,runner_name,started_at,finished_at) values (?,?,?,?,?,?,?,?,?)",jobId,id,job.externalJobId(),job.name(),job.status().name(),job.conclusion().name(),job.runnerName(),job.startedAt(),job.finishedAt()); for(PipelineStep step:job.steps()) { jdbc.update("insert into pipeline_steps(id,pipeline_job_id,step_number,name,status,conclusion,started_at,finished_at) values (?,?,?,?,?,?,?,?)",UUID.randomUUID(),jobId,step.number(),step.name(),step.status().name(),step.conclusion().name(),step.startedAt(),step.finishedAt()); steps++; } }
        return new PersistedPipelineRun(id,run.attempt(),run.jobs().size(),steps,now);
    }
    private UUID findOrCreateRepository(PipelineRun run, Instant now) { UUID id=jdbc.query("select id from repositories where provider=? and owner=? and name=?",rs->rs.next()?(UUID)rs.getObject(1):null,run.provider().name(),run.repositoryOwner(),run.repositoryName()); if(id==null){id=UUID.randomUUID();jdbc.update("insert into repositories(id,provider,owner,name,created_at,updated_at) values (?,?,?,?,?,?)",id,run.provider().name(),run.repositoryOwner(),run.repositoryName(),now,now);} return id; }
    private UUID findRun(UUID repositoryId,long external,int attempt){return jdbc.query("select id from pipeline_runs where repository_id=? and external_run_id=? and attempt=?",rs->rs.next()?(UUID)rs.getObject(1):null,repositoryId,external,attempt);}
}
