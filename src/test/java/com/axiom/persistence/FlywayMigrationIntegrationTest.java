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
}
