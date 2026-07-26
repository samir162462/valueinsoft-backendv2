package com.example.valueinsoftbackend.notification;

import com.example.valueinsoftbackend.notification.config.NotificationCipherProperties;
import com.example.valueinsoftbackend.notification.model.NotificationDevice;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationDevice;
import com.example.valueinsoftbackend.notification.repository.DbNotificationPushOutbox;
import com.example.valueinsoftbackend.notification.service.DeliveryKeyFactory;
import com.example.valueinsoftbackend.notification.service.NotificationDeviceService;
import com.example.valueinsoftbackend.notification.service.NotificationTokenCipher;
import com.example.valueinsoftbackend.notification.service.PushPayloadBuilder;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "VLS_NOTIFICATION_PHASE3_DB_URL", matches = ".+")
class NotificationPhase3ExternalPostgresIT {
    @Test
    void deliveryKeyParityDedupAtomicityAndQueueRecovery() {
        Harness harness = harness();
        JdbcTemplate jdbc = harness.jdbc();
        DeliveryKeyFactory keys = new DeliveryKeyFactory();
        Random random = new Random(3_026_0726L);

        for (int index = 0; index < 200; index++) {
            int companyId = random.nextInt(1, 100_000);
            long eventId = random.nextLong(1, Long.MAX_VALUE);
            int userId = random.nextInt(1, Integer.MAX_VALUE);
            long deviceId = random.nextLong(1, Long.MAX_VALUE);
            int version = random.nextInt(1, 10);
            byte[] javaKey = keys.create(
                    companyId, eventId, userId, deviceId, "push", version);
            byte[] sqlKey = jdbc.queryForObject(
                    "SELECT public.notification_delivery_key(?,?,?,?,?,?)",
                    byte[].class,
                    companyId, eventId, userId, deviceId, "push", version);
            assertThat(javaKey).containsExactly(sqlKey);
        }

        byte[] boundaryKey = keys.create(99001, 9001, 88001, 77001, "push", 1);
        cleanup(jdbc, boundaryKey);
        OffsetDateTime monthEnd = OffsetDateTime.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.lastDayOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(900_000_000);
        OffsetDateTime nextMonth = monthEnd.plusNanos(200_000_000);
        assertThat(reserve(jdbc, boundaryKey, 99001, 9001, 88001, 77001)).isOne();
        UUID firstUuid = UUID.randomUUID();
        insertOutbox(jdbc, monthEnd, firstUuid, boundaryKey, 99001, 9001, 88001, 77001,
                "collapse-boundary", "pending", 0, null);
        jdbc.update("UPDATE public.notification_delivery_dedup SET outbox_uuid=? WHERE delivery_key=?",
                firstUuid, boundaryKey);
        assertThat(reserve(jdbc, boundaryKey, 99001, 9001, 88001, 77001)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.notification_push_outbox WHERE delivery_key=?",
                Integer.class, boundaryKey)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT created_at FROM public.notification_push_outbox WHERE outbox_uuid=?",
                OffsetDateTime.class, firstUuid)).isEqualTo(monthEnd);
        assertThat(nextMonth.getMonth()).isNotEqualTo(monthEnd.getMonth());
        cleanup(jdbc, boundaryKey);

        byte[] rollbackKey = keys.create(99001, 9002, 88001, 77001, "push", 1);
        cleanup(jdbc, rollbackKey);
        assertThatThrownBy(() -> harness.tx().executeWithoutResult(status -> {
            reserve(jdbc, rollbackKey, 99001, 9002, 88001, 77001);
            insertOutbox(jdbc, OffsetDateTime.now(), UUID.randomUUID(), rollbackKey,
                    99001, 9002, 88001, 77001, "rollback", "pending", 0, null);
            throw new SimulatedCrash();
        })).isInstanceOf(SimulatedCrash.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.notification_delivery_dedup WHERE delivery_key=?",
                Integer.class, rollbackKey)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.notification_push_outbox WHERE delivery_key=?",
                Integer.class, rollbackKey)).isZero();

        DbNotificationPushOutbox outbox = new DbNotificationPushOutbox(jdbc);
        UUID aggregateRecipient = UUID.randomUUID();
        NotificationDevice aggregateDevice = new NotificationDevice(
                77002L, UUID.randomUUID(), 88001, 99001, null,
                "aggregate-device", "fcm", "com.valueinsoft.phase3",
                "none", "android", 1L, new byte[]{1}, "k1", new byte[32],
                "en", "UTC", 1, "active", 0, null, OffsetDateTime.now());
        NotificationEvent firstEvent = event(9010);
        NotificationEvent secondEvent = event(9011);
        byte[] firstAggregateKey = keys.create(
                99001, firstEvent.eventId(), 88001, 77002, "push", 1);
        byte[] secondAggregateKey = keys.create(
                99001, secondEvent.eventId(), 88001, 77002, "push", 1);
        cleanup(jdbc, firstAggregateKey);
        cleanup(jdbc, secondAggregateKey);
        PushPayloadBuilder.BuiltPush payload = new PushPayloadBuilder.BuiltPush(
                Map.of(), "{}", 2, 0, false);
        Boolean firstCreated = harness.tx().execute(status -> outbox.reserveAndInsert(
                firstAggregateKey, 99001, 88001, 1, aggregateRecipient,
                firstEvent, aggregateDevice, payload, 1, 6, null));
        Boolean secondCreated = harness.tx().execute(status -> outbox.reserveAndInsert(
                secondAggregateKey, 99001, 88001, 1, aggregateRecipient,
                secondEvent, aggregateDevice, payload, 1, 6, null));
        Boolean duplicateCreated = harness.tx().execute(status -> outbox.reserveAndInsert(
                firstAggregateKey, 99001, 88001, 1, aggregateRecipient,
                firstEvent, aggregateDevice, payload, 1, 6, null));
        assertThat(firstCreated).isTrue();
        assertThat(secondCreated).isTrue();
        assertThat(duplicateCreated).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.notification_push_outbox
                WHERE delivery_key IN (?,?) AND collapse_key=?
                """, Integer.class,
                firstAggregateKey, secondAggregateKey, aggregateRecipient.toString()))
                .isEqualTo(2);
        cleanup(jdbc, firstAggregateKey);
        cleanup(jdbc, secondAggregateKey);

        byte[] deadKey = keys.create(99001, 9003, 88001, 77001, "push", 1);
        byte[] expiredKey = keys.create(99001, 9004, 88001, 77001, "push", 1);
        cleanup(jdbc, deadKey);
        cleanup(jdbc, expiredKey);
        insertOutbox(jdbc, OffsetDateTime.now(), UUID.randomUUID(), deadKey,
                99001, 9003, 88001, 77001, "dead", "dead", 6, null);
        assertThat(outbox.claim(20, "phase3", 120)).isEmpty();
        assertThat(outbox.releaseExpiredClaims(20, 60, "phase3-reaper")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT attempt_count FROM public.notification_push_outbox
                WHERE delivery_key=?
                """, Integer.class, deadKey)).isEqualTo(6);

        insertOutbox(jdbc, OffsetDateTime.now(), UUID.randomUUID(), expiredKey,
                99001, 9004, 88001, 77001, "expired", "claimed", 1,
                OffsetDateTime.now().minusMinutes(1));
        assertThat(outbox.releaseExpiredClaims(20, 60, "phase3-reaper")).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT status FROM public.notification_push_outbox
                WHERE delivery_key=?
                """, String.class, expiredKey)).isEqualTo("failed");
        cleanup(jdbc, deadKey);
        cleanup(jdbc, expiredKey);
    }

    @Test
    void deviceLifecycleAuditsEveryBindingReasonAndCancelsQueuedRows() {
        Harness harness = harness();
        JdbcTemplate jdbc = harness.jdbc();
        int companyA = 99101;
        int companyB = 99102;
        int userA = 9910101;
        int userB = 9910102;
        seedIdentity(jdbc, companyA, userA, "phase3_user_a");
        seedIdentity(jdbc, companyB, userB, "phase3_user_b");
        jdbc.update("DELETE FROM public.notification_device WHERE user_id IN (?,?)",
                userA, userB);

        NotificationCipherProperties cipherProperties = new NotificationCipherProperties();
        cipherProperties.setActiveKeyId("k1");
        cipherProperties.setKeys(Map.of("k1", base64((byte) 12)));
        cipherProperties.setTokenHashPepper(base64((byte) 31));
        DbNotificationDevice repository = new DbNotificationDevice(jdbc);
        NotificationDeviceService service = new NotificationDeviceService(
                repository, new NotificationTokenCipher(cipherProperties));
        OffsetDateTime now = OffsetDateTime.now();

        NotificationDevice tokenRotation = register(
                harness, service, companyA, userA, "install-rotate", "token-r1", now);
        NotificationDevice rotated = register(
                harness, service, companyA, userA, "install-rotate", "token-r2", now.plusSeconds(1));
        assertThat(rotated.bindingVersion()).isEqualTo(tokenRotation.bindingVersion() + 1);
        NotificationDevice staleReport = register(
                harness, service, companyA, userA, "install-rotate", "token-old",
                now.minusSeconds(1));
        assertThat(staleReport.credentialHash()).containsExactly(rotated.credentialHash());

        NotificationDevice logout = register(
                harness, service, companyA, userA, "install-logout", "token-logout", now);
        byte[] queuedKey = new DeliveryKeyFactory().create(
                companyA, 9301, userA, logout.deviceId(), "push", 1);
        cleanup(jdbc, queuedKey);
        insertOutbox(jdbc, OffsetDateTime.now(), UUID.randomUUID(), queuedKey,
                companyA, 9301, userA, logout.deviceId(), "logout", "pending", 0, null);
        harness.tx().executeWithoutResult(status ->
                service.logout(companyA, userA, "install-logout"));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM public.notification_push_outbox WHERE delivery_key=?
                """, String.class, queuedKey)).isEqualTo("cancelled");
        register(harness, service, companyA, userA,
                "install-logout", "token-logout", now.plusSeconds(2));

        register(harness, service, companyA, userA,
                "install-shift", "token-shift", now);
        harness.tx().executeWithoutResult(status ->
                service.shiftClose(companyA, userA, "install-shift"));

        register(harness, service, companyA, userA,
                "install-switch", "token-company", now);
        register(harness, service, companyB, userB,
                "install-switch", "token-company", now.plusSeconds(1));

        seedIdentity(jdbc, companyB, userA, "phase3_user_a");
        register(harness, service, companyB, userA,
                "install-user-switch", "token-user", now);
        register(harness, service, companyB, userB,
                "install-user-switch", "token-user", now.plusSeconds(1));

        register(harness, service, companyA, userA,
                "install-token-owner", "token-reassigned", now);
        register(harness, service, companyA, userA,
                "install-token-new", "token-reassigned", now.plusSeconds(1));

        NotificationDevice invalidated = register(
                harness, service, companyA, userA,
                "install-invalidate", "token-invalidate", now);
        harness.tx().executeWithoutResult(status -> repository.invalidate(
                invalidated.deviceId(), "UNREGISTERED", OffsetDateTime.now(),
                false, true, userA));

        NotificationDevice support = register(
                harness, service, companyA, userA,
                "install-support", "token-support", now);
        harness.tx().executeWithoutResult(status ->
                service.adminRevoke(companyA, userA, support.deviceUuid()));

        assertThat(jdbc.queryForList("""
                SELECT DISTINCT reason
                FROM public.notification_device_binding_audit
                WHERE company_id IN (?,?)
                """, String.class, companyA, companyB))
                .contains(
                        "logout", "shift_close", "company_switch", "user_switch",
                        "support_revocation", "token_reassigned", "token_rotated",
                        "provider_invalidated", "reactivated");
    }

    private static NotificationDevice register(
            Harness harness,
            NotificationDeviceService service,
            int companyId,
            int userId,
            String installId,
            String token,
            OffsetDateTime reportedAt) {
        var view = harness.tx().execute(status -> service.register(
                companyId, null, userId,
                new NotificationDeviceService.RegistrationCommand(
                        installId, "fcm", "com.valueinsoft.phase3", "none",
                        "android", token, reportedAt, "1.0", "15",
                        1, "en", "UTC")));
        return new DbNotificationDevice(harness.jdbc())
                .findByUuid(view.deviceUuid()).orElseThrow();
    }

    private static int reserve(
            JdbcTemplate jdbc, byte[] key, int companyId,
            long eventId, int userId, long deviceId) {
        return jdbc.update("""
                INSERT INTO public.notification_delivery_dedup (
                    delivery_key, company_id, event_id, user_id, device_id,
                    channel, payload_version, expires_at
                ) VALUES (?, ?, ?, ?, ?, 'push', 1, NOW()+INTERVAL '30 days')
                ON CONFLICT (delivery_key) DO NOTHING
                """, key, companyId, eventId, userId, deviceId);
    }

    private static NotificationEvent event(long eventId) {
        return new NotificationEvent(
                eventId, "inventory.stock.low", null, null,
                "product", 1L, Map.of(), "normal", "stock:1",
                "phase3-test", null, "phase3-" + eventId, 180,
                java.time.Instant.now());
    }

    private static void insertOutbox(
            JdbcTemplate jdbc,
            OffsetDateTime createdAt,
            UUID uuid,
            byte[] key,
            int companyId,
            long eventId,
            int userId,
            long deviceId,
            String collapseKey,
            String status,
            int attemptCount,
            OffsetDateTime claimExpiresAt) {
        jdbc.update("""
                INSERT INTO public.notification_push_outbox (
                    created_at, outbox_uuid, delivery_key, company_id, event_id,
                    recipient_id, recipient_uuid, user_id, device_id,
                    device_binding_version, provider, priority, payload,
                    payload_version, payload_bytes, collapse_key, ttl_seconds,
                    status, attempt_count, max_attempts,
                    claimed_by, claimed_at, claim_expires_at
                ) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, 1, 'fcm', 'normal',
                          '{}'::jsonb, 1, 2, ?, 86400, ?, ?, 6,
                          CASE WHEN ?='claimed' THEN 'phase3' END,
                          CASE WHEN ?='claimed' THEN NOW() END, ?)
                """, createdAt, uuid, key, companyId, eventId, UUID.randomUUID(),
                userId, deviceId, collapseKey, status, attemptCount,
                status, status, claimExpiresAt);
    }

    private static void cleanup(JdbcTemplate jdbc, byte[] key) {
        jdbc.update("DELETE FROM public.notification_push_outbox WHERE delivery_key=?", key);
        jdbc.update("DELETE FROM public.notification_delivery_dedup WHERE delivery_key=?", key);
    }

    private static void seedIdentity(
            JdbcTemplate jdbc, int companyId, int userId, String userName) {
        jdbc.update("""
                INSERT INTO public."Company" (id, "companyName", currency)
                VALUES (?, 'Phase 3 Co', 'EGP') ON CONFLICT (id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.tenants
                    (tenant_id, package_id, template_id, status, bootstrap_source)
                VALUES (?, 'enterprise', 'general_business', 'active', 'bootstrap')
                ON CONFLICT (tenant_id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.users
                    (id, "userName", "userPassword", "userRole", "creationTime")
                VALUES (?, ?, 'x', 'Owner', NOW()) ON CONFLICT (id) DO NOTHING
                """, userId, userName);
    }

    private static String base64(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static Harness harness() {
        String url = requireDisposableDatabaseUrl();
        String user = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE3_DB_USER", "postgres");
        String password = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE3_DB_PASSWORD", "");
        Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .load()
                .migrate();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(url, user, password);
        DataSourceTransactionManager manager =
                new DataSourceTransactionManager(dataSource);
        return new Harness(
                new JdbcTemplate(dataSource), new TransactionTemplate(manager));
    }

    private static String requireDisposableDatabaseUrl() {
        String url = System.getenv("VLS_NOTIFICATION_PHASE3_DB_URL");
        if (url == null || !url.matches(".*/notif_[A-Za-z0-9_-]+(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "Phase 3 test refuses non-disposable databases; "
                            + "database name must start with notif_");
        }
        return url;
    }

    private record Harness(JdbcTemplate jdbc, TransactionTemplate tx) {
    }

    private static final class SimulatedCrash extends RuntimeException {
    }
}
