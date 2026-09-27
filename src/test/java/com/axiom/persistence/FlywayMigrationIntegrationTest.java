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
    @Test void migrationsReachV8WithCorrelationColumns() throws Exception {
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
            assertThat(version.getInt(1)).isEqualTo(8);
            var columns = columnStatement.executeQuery();
            columns.next();
            assertThat(columns.getInt(1)).isEqualTo(2);
        }
    }
}
