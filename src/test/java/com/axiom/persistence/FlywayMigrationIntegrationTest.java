package com.axiom.persistence;

import com.axiom.IntegrationTestSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIntegrationTest extends IntegrationTestSupport {
    @Autowired private DataSource dataSource;
    @Test void initialSchemaIsAvailableInPostgres() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("select count(*) from information_schema.tables where table_name = 'failure_evidence'")) {
            var result = statement.executeQuery(); result.next(); assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
    @Test void migrationsReachV10WithCorrelationComparisonAndRelevanceEvidence() throws Exception {
        try (var connection = dataSource.getConnection();
                var versionStatement = connection.prepareStatement(
                        "select max(version::integer) from flyway_schema_history where success");
                var columnStatement = connection.prepareStatement(
                        """
                        select count(*) from information_schema.columns
                        where table_name='test_case_executions'
                          and column_name in ('correlated_failure_event_id','correlation_strength')
                        """)) {
            var version = versionStatement.executeQuery();
            version.next();
            assertThat(version.getInt(1)).isEqualTo(10);
            var columns = columnStatement.executeQuery();
            columns.next();
            assertThat(columns.getInt(1)).isEqualTo(2);
            try (var metadataStatement = connection.prepareStatement(
                    """
                    select count(*) from information_schema.columns
                    where table_name='pipeline_runs'
                      and column_name in ('base_sha','event_name')
                    """)) {
                var metadataColumns = metadataStatement.executeQuery();
                metadataColumns.next();
                assertThat(metadataColumns.getInt(1)).isEqualTo(2);
            }
            try (var relevanceStatement = connection.prepareStatement(
                    """
                    select count(*) from information_schema.tables
                    where table_name in ('change_relevance_evidence','relevance_related_files')
                    """)) {
                var relevanceTables = relevanceStatement.executeQuery();
                relevanceTables.next();
                assertThat(relevanceTables.getInt(1)).isEqualTo(2);
            }
        }
    }
}
