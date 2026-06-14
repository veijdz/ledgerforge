package com.ledgerforge.platform;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Integration test proving the persistence baseline: a real PostgreSQL 17 (Testcontainers) is
 * reachable and Flyway applied {@code V1__baseline.sql} on startup (NUNCA H2).
 *
 * <p>{@code @ServiceConnection} wires Spring Boot's DataSource straight to the container, so no
 * datasource properties are set by hand; Boot's Flyway autoconfiguration (from
 * spring-boot-starter-flyway) runs the migration on startup against that DataSource.
 */
@SpringBootTest
@Testcontainers
class FlywayMigrationIntegrationTest {

  // Static singleton container: started once for the class. @ServiceConnection supplies the
  // jdbc url / credentials to Boot's datasource automatically.
  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

  @Autowired DataSource dataSource;

  @Autowired JdbcTemplate jdbcTemplate;

  @Test
  void connectsToRealPostgres() throws Exception {
    assertThat(postgres.isRunning()).isTrue();
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.isValid(1)).isTrue();
      assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
    }
  }

  @Test
  void flywayAppliedBaselineMigration() {
    Boolean success =
        jdbcTemplate.queryForObject(
            "SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
    assertThat(success).isTrue();
  }

  @Test
  void baselineMarkerTableExists() {
    Integer rows =
        jdbcTemplate.queryForObject("SELECT count(*) FROM schema_baseline", Integer.class);
    assertThat(rows).isEqualTo(1);
  }
}
