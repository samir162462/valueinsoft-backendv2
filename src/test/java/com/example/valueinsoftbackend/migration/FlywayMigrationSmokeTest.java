package com.example.valueinsoftbackend.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-9 keystone test (also validates P1-8): boots a real PostgreSQL, runs the FULL Flyway
 * migration set (V0 baseline -> V1 -> ... -> latest) against an empty database, and asserts
 * the schema is built end to end. This is the test that proves a fresh Postgres can be built
 * by Flyway alone — the thing that was impossible before the V0 legacy baseline.
 *
 * <p>Runs only where a Docker runtime is available (CI / local Docker). Uses the pgvector
 * image so the AI knowledge migration (V98) works whether or not it enables the vector
 * extension, and because the base image ships the contrib modules (pgcrypto) used by V50.</p>
 *
 * <p>No Spring context is started here on purpose — this isolates "do the migrations apply?"
 * from "does the whole app boot?" (secrets, AI, S3). Spring-context Postgres tests extend
 * {@code AbstractPostgresIntegrationTest}.</p>
 */
@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("vls_test")
            .withUsername("vls")
            .withPassword("vls");

    @Test
    void flywayBuildsCompleteSchemaFromScratch() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();

        assertTrue(result.success, "Flyway migration must succeed on a fresh PostgreSQL");
        assertTrue(result.migrationsExecuted > 0, "At least the baseline + migrations should run");

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            // Legacy base (created by the V0 baseline).
            assertTableExists(connection, "Company");
            assertTableExists(connection, "Branch");
            assertTableExists(connection, "users");
            assertTableExists(connection, "PosProduct");
            assertTableExists(connection, "SupplierBProduct");
            assertTableExists(connection, "supplier "); // trailing space is intentional

            // Modern tables (created by later migrations) prove the chain ran on top of V0.
            assertTableExists(connection, "finance_journal_entry");
            assertTableExists(connection, "billing_invoices");
            assertTableExists(connection, "inventory_product");
            assertTableExists(connection, "platform_capabilities");
            assertTableExists(connection, "inventory_tenant_schema_version");
            assertFunctionExists(connection, "ensure_inventory_workspace_receipt_foundation_for_tenant");
            assertFunctionExists(connection, "inventory_tenant_schema_drift");

            // Transient legacy table was created (V0) and then dropped (V119).
            assertTableAbsent(connection, "CompanySubscription");

            // P1-7 immutability trigger was installed by V139.
            assertTriggerExists(connection, "trg_finance_journal_entry_immutable");
        }
    }

    @Test
    void serializedUnitCostBackfillToleratesInvalidLegacyImeiRows() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS c_999999 CASCADE");
            statement.execute("CREATE SCHEMA c_999999");
            statement.execute("""
                    CREATE TABLE c_999999.inventory_product_unit (
                        product_unit_id BIGSERIAL PRIMARY KEY,
                        product_id BIGINT NOT NULL,
                        branch_id BIGINT NOT NULL,
                        tracking_type TEXT NOT NULL,
                        unit_identifier TEXT NOT NULL,
                        imei TEXT,
                        serial_number TEXT,
                        purchase_reference_type TEXT,
                        purchase_reference_id TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE c_999999.\"InventoryTransactions_1080\" (
                        \"transId\" BIGINT PRIMARY KEY,
                        \"transTotal\" NUMERIC(19,4) NOT NULL,
                        \"NumItems\" INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO c_999999.inventory_product_unit (
                        product_id, branch_id, tracking_type, unit_identifier, imei,
                        purchase_reference_type, purchase_reference_id
                    ) VALUES (
                        1, 1080, 'IMEI', '12345678911111', '12345678911111',
                        'INVENTORY_TRANSACTION', '1'
                    )
                    """);
            statement.execute("""
                    INSERT INTO c_999999.\"InventoryTransactions_1080\"
                        (\"transId\", \"transTotal\", \"NumItems\")
                    VALUES (1, 90000, 1)
                    """);
            statement.execute("SELECT public.ensure_serialized_inventory_imei_constraints_for_tenant('c_999999')");

            statement.execute("SELECT public.ensure_serialized_inventory_unit_costing_for_tenant('c_999999')");

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT acquisition_cost
                    FROM c_999999.inventory_product_unit
                    WHERE product_unit_id = 1
                    """)) {
                assertTrue(resultSet.next());
                assertEquals("90000.0000", resultSet.getBigDecimal("acquisition_cost").toPlainString());
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT convalidated
                    FROM pg_constraint
                    WHERE conrelid = 'c_999999.inventory_product_unit'::regclass
                      AND conname = 'inventory_product_unit_imei_luhn_ck'
                    """)) {
                assertTrue(resultSet.next(), "The IMEI constraint must be restored after the cost backfill");
                assertFalse(resultSet.getBoolean("convalidated"),
                        "Historical invalid IMEIs must remain tolerated while new writes are checked");
            }
        }
    }

    private void assertTableExists(Connection connection, String tableName) throws Exception {
        assertTrue(tableExists(connection, tableName),
                "Expected public.\"" + tableName + "\" to exist after migration");
    }

    private void assertTableAbsent(Connection connection, String tableName) throws Exception {
        assertTrue(!tableExists(connection, tableName),
                "Expected public.\"" + tableName + "\" to be absent (dropped by a later migration)");
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        String sql = "SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void assertTriggerExists(Connection connection, String triggerName) throws Exception {
        String sql = "SELECT 1 FROM pg_trigger WHERE tgname = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, triggerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertNotNull(resultSet);
                assertTrue(resultSet.next(), "Expected trigger " + triggerName + " to exist (V139)");
            }
        }
    }

    private void assertFunctionExists(Connection connection, String functionName) throws Exception {
        String sql = "SELECT 1 FROM pg_proc WHERE proname = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, functionName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "Expected function " + functionName + " to exist");
            }
        }
    }
}
