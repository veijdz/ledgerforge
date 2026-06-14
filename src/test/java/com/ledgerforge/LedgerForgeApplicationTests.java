package com.ledgerforge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Lightweight smoke test: the context boots without infrastructure. The datasource and Flyway
// autoconfigurations need a real database (exercised by FlywayMigrationIntegrationTest via
// Testcontainers), so they are excluded here to keep this a fast, Docker-free context check.
@SpringBootTest(
    properties =
        "spring.autoconfigure.exclude="
            + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class LedgerForgeApplicationTests {

  @Test
  void contextLoads() {}
}
