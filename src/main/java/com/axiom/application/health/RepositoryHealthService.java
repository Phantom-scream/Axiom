package com.axiom.application.health;

import com.axiom.api.error.ResourceNotFoundException;
import com.axiom.domain.failure.FailureClassification;
import com.axiom.domain.health.RepositoryHealth;
import com.axiom.domain.relevance.ChangeRelevance;
import com.axiom.domain.test.TestStabilityClass;
import com.axiom.domain.triage.RerunRecommendation;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryHealthService {
    public static final int DEFAULT_DAYS = 30;
    public static final int DEFAULT_MAX_RUNS = 200;
    public static final int MAX_DAYS = 3_650;
    public static final int MAX_RUNS = 1_000;
    private static final int TOP_FINGERPRINTS = 10;
    private static final String SELECTED_RUNS = """
            with selected_runs as (
                select id,conclusion,
                       coalesce(finished_at,started_at,ingested_at,created_at) occurred_at
                from pipeline_runs
                where repository_id=?
                  and coalesce(finished_at,started_at,ingested_at,created_at)>=?
                order by coalesce(finished_at,started_at,ingested_at,created_at) desc,id
                limit ?
            )
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RepositoryHealthService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RepositoryHealth get(UUID repositoryId, int days, int maxRuns) {
        validate(days, maxRuns);
        requireRepository(repositoryId);
        Timestamp cutoff = Timestamp.from(Instant.now(clock).minus(days, ChronoUnit.DAYS));
        RunCounts runs = runCounts(repositoryId, cutoff, maxRuns);
        return new RepositoryHealth(
                repositoryId,
                new RepositoryHealth.Window(days, maxRuns, runs.total()),
                new RepositoryHealth.Runs(
                        runs.total(),
                        runs.successful(),
                        runs.failed(),
                        runs.cancelled(),
                        successRate(runs)),
                classificationBreakdown(repositoryId, cutoff, maxRuns),
                rerunBreakdown(repositoryId, cutoff, maxRuns),
                relevanceBreakdown(repositoryId, cutoff, maxRuns),
                stabilityCounts(repositoryId, cutoff, maxRuns),
                topFingerprints(repositoryId, cutoff, maxRuns));
    }

    private RunCounts runCounts(UUID repositoryId, Timestamp cutoff, int maxRuns) {
        return jdbc.query(
                SELECTED_RUNS
                        + """
                        select count(*) total,
                               count(*) filter(where conclusion='SUCCESS') successful,
                               count(*) filter(where conclusion in ('FAILURE','TIMED_OUT')) failed,
                               count(*) filter(where conclusion='CANCELLED') cancelled
                        from selected_runs
                        """,
                rs -> {
                    rs.next();
                    return new RunCounts(
                            rs.getInt("total"),
                            rs.getInt("successful"),
                            rs.getInt("failed"),
                            rs.getInt("cancelled"));
                },
                repositoryId,
                cutoff,
                maxRuns);
    }

    private Map<FailureClassification, Long> classificationBreakdown(
            UUID repositoryId, Timestamp cutoff, int maxRuns) {
        Map<FailureClassification, Long> result = zeroMap(FailureClassification.class);
        jdbc.query(
                        SELECTED_RUNS
                                + """
                                select classification,count(*) signal_count
                                from (
                                    select distinct fd.pipeline_run_id,fd.fingerprint,fd.classification
                                    from failure_diagnoses fd
                                    join selected_runs sr on sr.id=fd.pipeline_run_id
                                    where fd.classifier_version='axiom-classifier-v1'
                                ) signals
                                group by classification
                                """,
                        (rs, row) -> new CountRow(
                                rs.getString("classification"), rs.getLong("signal_count")),
                        repositoryId,
                        cutoff,
                        maxRuns)
                .forEach(row -> result.put(
                        FailureClassification.valueOf(row.key()), row.count()));
        return result;
    }

    private Map<RerunRecommendation, Long> rerunBreakdown(
            UUID repositoryId, Timestamp cutoff, int maxRuns) {
        Map<RerunRecommendation, Long> result = zeroMap(RerunRecommendation.class);
        jdbc.query(
                        SELECTED_RUNS
                                + """
                                select triage.rerun_recommendation,count(*) recommendation_count
                                from selected_runs sr
                                join lateral (
                                    select rerun_recommendation
                                    from pipeline_triage_results
                                    where pipeline_run_id=sr.id
                                    order by created_at desc,triage_version desc
                                    limit 1
                                ) triage on true
                                group by triage.rerun_recommendation
                                """,
                        (rs, row) -> new CountRow(
                                rs.getString("rerun_recommendation"),
                                rs.getLong("recommendation_count")),
                        repositoryId,
                        cutoff,
                        maxRuns)
                .forEach(row -> result.put(RerunRecommendation.valueOf(row.key()), row.count()));
        return result;
    }

    private Map<ChangeRelevance, Long> relevanceBreakdown(
            UUID repositoryId, Timestamp cutoff, int maxRuns) {
        Map<ChangeRelevance, Long> result = zeroMap(ChangeRelevance.class);
        jdbc.query(
                        SELECTED_RUNS
                                + """
                                select relevance,count(*) relevance_count
                                from change_relevance_results cr
                                join selected_runs sr on sr.id=cr.pipeline_run_id
                                where cr.analyzer_version='change-relevance-v1'
                                group by relevance
                                """,
                        (rs, row) -> new CountRow(
                                rs.getString("relevance"), rs.getLong("relevance_count")),
                        repositoryId,
                        cutoff,
                        maxRuns)
                .forEach(row -> result.put(ChangeRelevance.valueOf(row.key()), row.count()));
        return result;
    }

    private RepositoryHealth.TestStability stabilityCounts(
            UUID repositoryId, Timestamp cutoff, int maxRuns) {
        Map<TestStabilityClass, Long> counts = zeroMap(TestStabilityClass.class);
        jdbc.query(
                        SELECTED_RUNS
                                + """
                                , selected_tests as (
                                    select distinct trim(t.stable_test_id) stable_test_id
                                    from test_case_executions t
                                    join selected_runs sr on sr.id=t.pipeline_run_id
                                )
                                select s.stability_class,count(*) stability_count
                                from test_stability_snapshots s
                                join selected_tests st on st.stable_test_id=trim(s.stable_test_id)
                                where s.classifier_version='stability-v1'
                                  and s.stability_class in (
                                      'SUSPECTED_FLAKY','FLAKY','CONSISTENTLY_FAILING')
                                group by s.stability_class
                                """,
                        (rs, row) -> new CountRow(
                                rs.getString("stability_class"),
                                rs.getLong("stability_count")),
                        repositoryId,
                        cutoff,
                        maxRuns)
                .forEach(row -> counts.put(TestStabilityClass.valueOf(row.key()), row.count()));
        return new RepositoryHealth.TestStability(
                counts.get(TestStabilityClass.SUSPECTED_FLAKY),
                counts.get(TestStabilityClass.FLAKY),
                counts.get(TestStabilityClass.CONSISTENTLY_FAILING));
    }

    private List<RepositoryHealth.TopFailureFingerprint> topFingerprints(
            UUID repositoryId, Timestamp cutoff, int maxRuns) {
        return jdbc.query(
                SELECTED_RUNS
                        + """
                        , signals as (
                            select fe.id,fe.fingerprint,fe.occurrence_count,sr.occurred_at,
                                   coalesce(diagnosis.classification,'UNKNOWN') classification
                            from failure_events fe
                            join selected_runs sr on sr.id=fe.pipeline_run_id
                            left join lateral (
                                select classification
                                from failure_diagnoses fd
                                where fd.failure_event_id=fe.id
                                  and fd.classifier_version='axiom-classifier-v1'
                                order by fd.created_at desc,fd.id
                                limit 1
                            ) diagnosis on true
                        )
                        select fingerprint,sum(occurrence_count) occurrence_count,
                               (array_agg(classification order by occurred_at desc,id))[1] classification,
                               min(occurred_at) first_seen,max(occurred_at) last_seen
                        from signals
                        group by fingerprint
                        order by sum(occurrence_count) desc,fingerprint
                        limit ?
                        """,
                (rs, row) -> new RepositoryHealth.TopFailureFingerprint(
                        rs.getString("fingerprint"),
                        rs.getLong("occurrence_count"),
                        FailureClassification.valueOf(rs.getString("classification")),
                        rs.getTimestamp("first_seen").toInstant(),
                        rs.getTimestamp("last_seen").toInstant()),
                repositoryId,
                cutoff,
                maxRuns,
                TOP_FINGERPRINTS);
    }

    private double successRate(RunCounts runs) {
        int applicable = runs.successful() + runs.failed();
        return applicable == 0 ? 0 : runs.successful() / (double) applicable;
    }

    private void validate(int days, int maxRuns) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS + ".");
        }
        if (maxRuns < 1 || maxRuns > MAX_RUNS) {
            throw new IllegalArgumentException(
                    "maxRuns must be between 1 and " + MAX_RUNS + ".");
        }
    }

    private void requireRepository(UUID repositoryId) {
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from repositories where id=?)",
                Boolean.class,
                repositoryId));
        if (!exists) {
            throw new ResourceNotFoundException("Repository " + repositoryId + " was not found.");
        }
    }

    private <E extends Enum<E>> Map<E, Long> zeroMap(Class<E> type) {
        Map<E, Long> values = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) values.put(value, 0L);
        return values;
    }

    private record RunCounts(int total, int successful, int failed, int cancelled) {}

    private record CountRow(String key, long count) {}
}
