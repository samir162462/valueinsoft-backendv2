package com.example.valueinsoftbackend.migration;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.DbNotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFanOutJob;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeedChange;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRecipient;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRecipientEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationTemplate;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import com.example.valueinsoftbackend.notification.service.CanonicalJsonService;
import com.example.valueinsoftbackend.notification.service.NotificationAggregationService;
import com.example.valueinsoftbackend.notification.service.NotificationFanOutService;
import com.example.valueinsoftbackend.notification.service.NotificationIdempotencyConflictException;
import com.example.valueinsoftbackend.notification.service.NotificationIdempotencyService;
import com.example.valueinsoftbackend.notification.service.NotificationPreviewRenderer;
import com.example.valueinsoftbackend.notification.service.NotificationPublisher;
import com.example.valueinsoftbackend.notification.service.NotificationTemplateRenderer;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("postgres")
@Testcontainers(disabledWithoutDocker = true)
class NotificationPhase1MigrationIT {

    private static final int COMPANY_ID = 9201;
    private static final String SCHEMA = "c_" + COMPANY_ID;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("vls_notification_phase1")
            .withUsername("vls")
            .withPassword("vls");

    @BeforeAll
    static void migrate() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        assertTrue(flyway.migrate().success, "Flyway migration must succeed");
    }

    @Test
    void publicCatalogPartitionsAndControlDefaultsExist() throws Exception {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            assertEquals(42, scalar(statement,
                    "SELECT COUNT(*) FROM public.notification_type_catalog"));
            assertEquals(84, scalar(statement,
                    "SELECT COUNT(*) FROM public.notification_template "
                            + "WHERE status = 'published'"));
            assertEquals(19, scalar(statement,
                    "SELECT COUNT(*) FROM public.notification_control_switch"));
            assertEquals(0, scalar(statement,
                    "SELECT COUNT(*) FROM public.notification_push_outbox_default"));
            assertEquals(0, scalar(statement,
                    "SELECT COUNT(*) FROM public.notification_delivery_attempt_default"));

            LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
            DateTimeFormatter suffix = DateTimeFormatter.ofPattern("yyyy_MM");
            for (int offset = 0; offset <= 3; offset++) {
                assertTrue(tableExists(connection, "public",
                                "notification_push_outbox_" + currentMonth.plusMonths(offset).format(suffix)),
                        "Expected current month plus three outbox partitions");
            }

            scalar(statement, "SELECT public.notification_partition_maintenance(3)");
            assertEquals(0, scalar(statement,
                    "SELECT public.notification_partition_maintenance(3)"),
                    "A second maintenance pass must be idempotent");
        }
    }

    @Test
    void bootstrapIsIdempotentAndCreatesSixTablesAndOneSequence() throws Exception {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("SELECT public.notification_bootstrap_tenant('" + SCHEMA + "')");
            statement.execute("SELECT public.notification_bootstrap_tenant('" + SCHEMA + "')");

            assertEquals(6, scalar(statement,
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = '" + SCHEMA + "' "
                            + "AND table_name LIKE 'notification_%'"));
            assertEquals(1, scalar(statement,
                    "SELECT COUNT(*) FROM information_schema.sequences "
                            + "WHERE sequence_schema = '" + SCHEMA + "' "
                            + "AND sequence_name = 'notification_feed_change_seq'"));
            assertTrue(indexExists(connection, SCHEMA, "uq_nr_open_group"));
            assertTrue(indexExists(connection, SCHEMA, "idx_nr_feed_active_branch"));
            assertTrue(indexExists(connection, SCHEMA, "idx_nfc_replay"));
        }
    }

    @Test
    void javaCompanyProvisioningInvokesTheFlywayBootstrap() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        DbCompany dbCompany = new DbCompany(
                jdbcTemplate,
                new NamedParameterJdbcTemplate(jdbcTemplate),
                new DbBranch(jdbcTemplate),
                POSTGRES.getUsername(),
                false);
        int companyId = 9202;

        jdbcTemplate.update(
                "INSERT INTO public.\"Company\" (id, \"companyName\", currency) VALUES (?, ?, ?)",
                companyId, "Notification Provisioning Co", "EGP");

        assertTrue(dbCompany.createCompanySchema(companyId));
        for (String objectName : List.of(
                TenantSqlIdentifiers.notificationEventTable(companyId),
                TenantSqlIdentifiers.notificationFanOutJobTable(companyId),
                TenantSqlIdentifiers.notificationRecipientTable(companyId),
                TenantSqlIdentifiers.notificationRecipientEventTable(companyId),
                TenantSqlIdentifiers.notificationFeedChangeTable(companyId),
                TenantSqlIdentifiers.notificationRecipientAuditTable(companyId))) {
            assertEquals(objectName, jdbcTemplate.queryForObject(
                    "SELECT to_regclass(?)::text", String.class, objectName));
        }
    }

    @Test
    void blueprintCheckConstraintsArePresent() throws Exception {
        Set<String> expected = Set.of(
                "chk_ntc_type_key", "chk_ntc_category", "chk_ntc_priority",
                "chk_ntc_preview_policy", "chk_ntc_status", "chk_ntc_retention",
                "chk_ntc_agg_window", "chk_ntc_preview_len", "chk_ntc_agg_needs_group",
                "chk_ntc_critical_rules", "chk_ntc_sensitive_preview",
                "chk_nt_locale", "chk_nt_status", "chk_nt_version", "chk_nt_published",
                "chk_nt_preview_reviewed", "chk_nt_preview_generic_static", "chk_nt_preview_len",
                "chk_nd_provider", "chk_nd_platform", "chk_nd_status", "chk_nd_apns_env",
                "chk_nd_failures", "chk_nd_binding", "chk_nd_revoked", "chk_ndba_reason",
                "chk_npg_min_priority", "chk_npg_digest", "chk_npg_quiet_pair",
                "chk_ndd_channel", "chk_ndd_key_len", "chk_ndd_expiry",
                "chk_npo_provider", "chk_npo_priority", "chk_npo_status", "chk_npo_attempts",
                "chk_npo_claim", "chk_npo_sent", "chk_npo_cancelled", "chk_npo_cancel_reason",
                "chk_npo_binding", "chk_npo_payload_obj", "chk_npo_payload_coarse",
                "chk_npo_payload_bytes", "chk_npo_replay", "chk_npo_broadcast",
                "chk_nda_error_class", "chk_nda_attempt_no",
                "chk_nb_scope", "chk_nb_status", "chk_nb_company_scope", "chk_nb_branch_scope",
                "chk_nb_counts", "chk_nb_planned", "chk_nbt_status", "chk_nbt_skip",
                "chk_nbt_skip_reason", "chk_nbt_materialized", "chk_nbb_status",
                "chk_nbb_claim", "chk_nbb_counts",
                "chk_ne_priority", "chk_ne_source", "chk_ne_params", "chk_ne_subject",
                "chk_ne_retention", "chk_ne_broadcast_source", "chk_nfj_mode",
                "chk_nfj_status", "chk_nfj_claim", "chk_nfj_mode_set", "chk_nfj_counts",
                "chk_nr_state", "chk_nr_render_status", "chk_nr_aggregate", "chk_nr_priority",
                "chk_nr_category", "chk_nr_archived", "chk_nr_read", "chk_nr_version",
                "chk_nr_archived_closes_group", "chk_nre_sequence", "chk_nfc_type",
                "chk_nra_to_state", "chk_nra_channel",
                "chk_ncs_scope", "chk_ncs_suppression", "chk_ncs_reason_required",
                "chk_ncs_until", "chk_ncs_version");

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT conname FROM pg_constraint WHERE contype = 'c'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.HashSet<String> actual = new java.util.HashSet<>();
                while (resultSet.next()) {
                    actual.add(resultSet.getString(1));
                }
                assertTrue(actual.containsAll(expected),
                        "Missing constraints: " + expected.stream()
                                .filter(name -> !actual.contains(name)).sorted().toList());
            }
        }
    }

    @Test
    void phase2PublishIdempotencyFanOutAndAggregationRunEndToEnd() {
        final int companyId = 9301;
        final int userId = 930101;
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO public."Company" (id, "companyName", currency)
                VALUES (?, 'Phase 2 Co', 'EGP') ON CONFLICT (id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.users
                    (id, "userName", "userPassword", "userRole", "creationTime")
                VALUES (?, 'phase2_user', 'x', 'Owner', NOW())
                ON CONFLICT (id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO public.tenants
                    (tenant_id, package_id, template_id, status, bootstrap_source)
                VALUES (?, 'enterprise', 'general_business', 'active', 'bootstrap')
                ON CONFLICT (tenant_id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.tenant_role_assignments
                    (tenant_id, user_id, role_id, status, source, scope_type)
                VALUES (?, ?, 'Owner', 'active', 'bootstrap', 'company')
                ON CONFLICT DO NOTHING
                """, companyId, userId);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS c_" + companyId);
        jdbc.execute("SELECT public.notification_bootstrap_tenant('c_" + companyId + "')");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CanonicalJsonService canonicalJson = new CanonicalJsonService(objectMapper);
        NotificationIdempotencyService idempotency =
                new NotificationIdempotencyService(canonicalJson);
        DbNotificationEvent events = new DbNotificationEvent(jdbc, canonicalJson, objectMapper);
        DbNotificationFanOutJob jobs = new DbNotificationFanOutJob(jdbc);
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gate.isEnabled(NotificationComponent.PUBLISH)).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> gates = mock(ObjectProvider.class);
        when(gates.getIfAvailable()).thenReturn(gate);
        NotificationPublisher publisher =
                new NotificationPublisher(properties, gates, idempotency, events, jobs);
        TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        NotificationRequest firstRequest = NotificationRequest.builder(
                        companyId, "marketing.campaign.published", "phase2:campaign:1")
                .subject("campaign", 1L)
                .params(java.util.Map.of("campaignId", 1, "campaignName", "Summer"))
                .build();
        var first = tx.execute(status -> publisher.publish(firstRequest));
        assertNotNull(first);
        assertTrue(first.created());
        var duplicate = tx.execute(status -> publisher.publish(firstRequest));
        assertNotNull(duplicate);
        assertFalse(duplicate.created());
        assertEquals(first.eventId(), duplicate.eventId());

        NotificationRequest conflict = NotificationRequest.builder(
                        companyId, "marketing.campaign.published", "phase2:campaign:1")
                .subject("campaign", 1L)
                .params(java.util.Map.of("campaignId", 1, "campaignName", "Changed"))
                .build();
        assertThrows(NotificationIdempotencyConflictException.class,
                () -> tx.execute(status -> publisher.publish(conflict)));

        NotificationAudienceResolver audience = new NotificationAudienceResolver(jdbc);
        DbNotificationCatalog catalog = new DbNotificationCatalog(jdbc);
        NotificationTemplateRenderer renderer = new NotificationTemplateRenderer(
                new DbNotificationTemplate(jdbc), new NotificationPreviewRenderer());
        NotificationAggregationService aggregation = new NotificationAggregationService(
                new DbNotificationRecipient(jdbc, canonicalJson),
                new DbNotificationRecipientEvent(jdbc),
                new DbNotificationFeedChange(jdbc));
        NotificationFanOutService fanOut = new NotificationFanOutService(
                jdbc, properties, jobs, events, catalog, audience, renderer, aggregation,
                new DataSourceTransactionManager(dataSource));

        var claimed = fanOut.claimAndDecide(companyId, "phase2-test").orElseThrow();
        assertNotNull(claimed.mode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM c_9301.notification_recipient",
                Integer.class));
        fanOut.materialize(claimed);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_9301.notification_recipient", Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_9301.notification_recipient_event", Integer.class));

        NotificationRequest secondRequest = NotificationRequest.builder(
                        companyId, "marketing.campaign.published", "phase2:campaign:2")
                .subject("campaign", 1L)
                .params(java.util.Map.of("campaignId", 1, "campaignName", "Summer update"))
                .build();
        tx.execute(status -> publisher.publish(secondRequest));
        properties.getFanOut().setSingleBatchThreshold(0);
        properties.getFanOut().setBatchSize(1);
        var cursorJob = fanOut.claimAndDecide(companyId, "phase2-test").orElseThrow();
        assertEquals(NotificationFanOutService.CURSOR, cursorJob.mode());
        fanOut.materialize(cursorJob);
        assertEquals(2, jdbc.queryForObject("""
                SELECT aggregate_count FROM c_9301.notification_recipient
                WHERE user_id = ?
                """, Integer.class, userId));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_9301.notification_recipient_event", Integer.class));
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static int scalar(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_name = ?")) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean indexExists(Connection connection, String schema, String index)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?")) {
            statement.setString(1, schema);
            statement.setString(2, index);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
