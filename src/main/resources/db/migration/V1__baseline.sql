-- V1 baseline migration for LedgerForge.
--
-- Intentionally minimal: this only proves the migration toolchain (Flyway against a
-- real Postgres via Testcontainers) is wired and records a row in flyway_schema_history.
-- The real ledger schema (accounts, journal) lands in V2 during Fase 1.6; do not model
-- any domain tables here.
--
-- A single trivial marker table keeps the migration non-empty and independently
-- verifiable (its existence is asserted by FlywayMigrationIntegrationTest).
CREATE TABLE schema_baseline (
    id          INTEGER     PRIMARY KEY,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO schema_baseline (id) VALUES (1);
