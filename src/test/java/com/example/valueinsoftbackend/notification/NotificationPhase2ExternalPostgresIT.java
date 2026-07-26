package com.example.valueinsoftbackend.notification;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationFanOutJob;
import com.example.valueinsoftbackend.notification.model.NotificationRequest;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.DbNotificationControl;
import com.example.valueinsoftbackend.notification.repository.DbNotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFanOutJob;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeedChange;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFeed;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRecipient;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRecipientEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRetention;
import com.example.valueinsoftbackend.notification.repository.DbNotificationTemplate;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import com.example.valueinsoftbackend.notification.service.CanonicalJsonService;
import com.example.valueinsoftbackend.notification.service.NotificationAggregationService;
import com.example.valueinsoftbackend.notification.service.NotificationFanOutService;
import com.example.valueinsoftbackend.notification.service.NotificationCursorCodec;
import com.example.valueinsoftbackend.notification.service.NotificationFeedService;
import com.example.valueinsoftbackend.notification.service.NotificationIdempotencyConflictException;
import com.example.valueinsoftbackend.notification.service.NotificationIdempotencyService;
import com.example.valueinsoftbackend.notification.service.NotificationPreviewRenderer;
import com.example.valueinsoftbackend.notification.service.NotificationPublisher;
import com.example.valueinsoftbackend.notification.service.NotificationSummaryService;
import com.example.valueinsoftbackend.notification.service.NotificationTemplateRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in smoke test for a disposable PostgreSQL database.
 * Set VLS_NOTIFICATION_PHASE2_DB_URL, USER and PASSWORD to run it.
 */
@EnabledIfEnvironmentVariable(named = "VLS_NOTIFICATION_PHASE2_DB_URL", matches = ".+")
class NotificationPhase2ExternalPostgresIT {
    @Test
    void controlChangePersistsSwitchAndAuditWithMonotonicVersion() {
        String databaseUrl = requireDisposableDatabaseUrl();
        String databaseUser = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_USER", "postgres");
        String databasePassword = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_PASSWORD", "");
        migrate(databaseUrl, databaseUser, databasePassword);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                databaseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DbNotificationControl controls =
                new DbNotificationControl(new NamedParameterJdbcTemplate(dataSource));
        TransactionTemplate tx = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        var disabled = tx.execute(status -> controls.change(
                NotificationComponent.FANOUT,
                false,
                "QUEUE",
                "Phase 2 external control test",
                null,
                0,
                "127.0.0.1",
                0));
        assertThat(disabled).isNotNull();
        assertThat(jdbc.queryForObject("""
                SELECT enabled FROM public.notification_control_switch
                WHERE scope='worker' AND component_key='FANOUT'
                """, Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM public.notification_control_audit
                WHERE control_version = ?
                """, Integer.class, disabled.controlVersion())).isOne();

        var enabled = tx.execute(status -> controls.change(
                NotificationComponent.FANOUT,
                true,
                "SUPPRESS",
                null,
                null,
                0,
                "127.0.0.1",
                0));
        assertThat(enabled).isNotNull();
        assertThat(enabled.controlVersion()).isGreaterThan(disabled.controlVersion());
        assertThat(jdbc.queryForObject("""
                SELECT enabled FROM public.notification_control_switch
                WHERE scope='worker' AND component_key='FANOUT'
                """, Boolean.class)).isTrue();
    }

    @Test
    void publishFanOutAndAggregationRunAgainstPostgres() throws Exception {
        int companyId = 1095;
        String databaseUrl = requireDisposableDatabaseUrl();
        String databaseUser = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_USER", "postgres");
        String databasePassword = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_PASSWORD", "");
        migrate(databaseUrl, databaseUser, databasePassword);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                databaseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO public."Company" (id, "companyName", currency)
                VALUES (?, 'Phase 2 External Co', 'EGP')
                ON CONFLICT (id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.tenants
                    (tenant_id, package_id, template_id, status, bootstrap_source)
                VALUES (?, 'enterprise', 'general_business', 'active', 'bootstrap')
                ON CONFLICT (tenant_id) DO NOTHING
                """, companyId);
        jdbc.update("DELETE FROM public.tenant_role_assignments WHERE tenant_id = ?",
                companyId);
        for (int offset = 1; offset <= 7; offset++) {
            int userId = companyId * 100 + offset;
            jdbc.update("""
                    INSERT INTO public.users
                        (id, "userName", "userPassword", "userRole", "creationTime")
                    VALUES (?, ?, 'x', 'Owner', NOW())
                    ON CONFLICT (id) DO NOTHING
                    """, userId, "phase2_external_" + offset);
            jdbc.update("""
                    INSERT INTO public.tenant_role_assignments
                        (tenant_id, user_id, role_id, status, source, scope_type)
                    VALUES (?, ?, 'Owner', 'active', 'bootstrap', 'company')
                    ON CONFLICT DO NOTHING
                    """, companyId, userId);
        }
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS c_" + companyId);
        jdbc.execute("SELECT public.notification_bootstrap_tenant('c_" + companyId + "')");
        jdbc.update("DELETE FROM c_1095.notification_fanout_job");
        jdbc.update("DELETE FROM c_1095.notification_recipient_event");
        jdbc.update("DELETE FROM c_1095.notification_feed_change");
        jdbc.update("DELETE FROM c_1095.notification_recipient_audit");
        jdbc.update("DELETE FROM c_1095.notification_recipient");
        jdbc.update("DELETE FROM c_1095.notification_event");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CanonicalJsonService canonical = new CanonicalJsonService(mapper);
        NotificationIdempotencyService idempotency =
                new NotificationIdempotencyService(canonical);
        DbNotificationEvent events = new DbNotificationEvent(jdbc, canonical, mapper);
        DbNotificationFanOutJob jobs = new DbNotificationFanOutJob(jdbc);
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gate.isEnabled(NotificationComponent.PUBLISH)).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gate);
        NotificationPublisher publisher =
                new NotificationPublisher(properties, provider, idempotency, events, jobs);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate tx = new TransactionTemplate(manager);

        NotificationRequest request = request(companyId, "phase2:external:1", "Campaign one");
        var first = tx.execute(status -> publisher.publish(request));
        var duplicate = tx.execute(status -> publisher.publish(request));
        assertThat(first).isNotNull();
        assertThat(first.created()).isTrue();
        assertThat(duplicate).isNotNull();
        assertThat(duplicate.eventId()).isEqualTo(first.eventId());
        assertThat(duplicate.created()).isFalse();
        assertThatThrownBy(() -> tx.execute(status ->
                publisher.publish(request(companyId, "phase2:external:1", "Changed"))))
                .isInstanceOf(NotificationIdempotencyConflictException.class);

        NotificationFanOutService fanOut = fanOut(jdbc, mapper, canonical, properties,
                events, jobs, manager);
        var claimed = fanOut.claimAndDecide(companyId, "external-test").orElseThrow();
        assertThat(claimed.mode()).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient", Integer.class))
                .isZero();
        fanOut.materialize(claimed);
        int audience = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT user_id) FROM public.tenant_role_assignments "
                        + "WHERE tenant_id=1095 AND status='active'", Integer.class);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient", Integer.class))
                .isEqualTo(audience);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event", Integer.class))
                .isEqualTo(audience);

        tx.execute(status -> publisher.publish(
                request(companyId, "phase2:external:2", "Campaign two")));
        properties.getFanOut().setSingleBatchThreshold(1);
        properties.getFanOut().setBatchSize(2);
        var cursorJob = fanOut.claimAndDecide(companyId, "external-test").orElseThrow();
        assertThat(cursorJob.mode()).isEqualTo(NotificationFanOutService.CURSOR);
        fanOut.materialize(cursorJob);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient "
                        + "WHERE aggregate_count=2", Integer.class))
                .isEqualTo(audience);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event", Integer.class))
                .isEqualTo(audience * 2);

        tx.execute(status -> publisher.publish(
                request(companyId, "phase2:external:concurrent", "Concurrent campaign")));
        var concurrentJob = fanOut.claimAndDecide(companyId, "external-concurrency")
                .orElseThrow();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var workerOne = executor.submit(() -> {
                start.await();
                fanOut.materialize(concurrentJob);
                return null;
            });
            var workerTwo = executor.submit(() -> {
                start.await();
                fanOut.materialize(concurrentJob);
                return null;
            });
            start.countDown();
            workerOne.get(30, TimeUnit.SECONDS);
            workerTwo.get(30, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient "
                        + "WHERE aggregate_count=3", Integer.class))
                .isEqualTo(audience);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event", Integer.class))
                .isEqualTo(audience * 3);

        int userId = jdbc.queryForObject("""
                SELECT MIN(user_id) FROM public.tenant_role_assignments
                WHERE tenant_id=1095 AND status='active'
                """, Integer.class);
        DbNotificationFeed dbFeed = new DbNotificationFeed(jdbc, mapper);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        NotificationSummaryService summaries =
                new NotificationSummaryService(dbFeed,
                        new NotificationAudienceResolver(jdbc), redis, mapper);
        NotificationFeedService feed = new NotificationFeedService(
                dbFeed, new DbNotificationFeedChange(jdbc),
                new DbNotificationRecipient(jdbc, canonical),
                new NotificationAudienceResolver(jdbc), new NotificationCursorCodec(),
                summaries);
        var page = feed.page(companyId, userId, null, null, null, null, 20);
        assertThat(page.items()).hasSize(1);
        var uuid = page.items().getFirst().recipientUuid();
        assertThat(feed.lineage(companyId, userId, uuid)).hasSize(3);
        assertThat(summaries.summary(companyId, userId).unseenCount()).isEqualTo(1);

        feed.markSeen(companyId, userId, java.util.List.of(uuid), "web");
        assertThat(summaries.summary(companyId, userId).unseenCount()).isZero();
        feed.markRead(companyId, userId, uuid, "web");
        assertThat(summaries.summary(companyId, userId).unreadCount()).isZero();
        feed.markClicked(companyId, userId, uuid, "web");
        feed.archive(companyId, userId, uuid, "web");
        assertThat(feed.detail(companyId, userId, uuid).state()).isEqualTo("archived");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient_audit
                WHERE user_id = ?
                """, Integer.class, userId)).isEqualTo(7);

        jdbc.update("DELETE FROM c_1095.notification_recipient_event");
        jdbc.update("DELETE FROM c_1095.notification_feed_change");
        jdbc.update("DELETE FROM c_1095.notification_recipient_audit");
        jdbc.update("DELETE FROM c_1095.notification_recipient");
        long loadEventId = jdbc.queryForObject(
                "SELECT MIN(event_id) FROM c_1095.notification_event", Long.class);
        jdbc.update("""
                INSERT INTO c_1095.notification_recipient (
                    user_id, type_key, category, first_event_id, latest_event_id,
                    aggregate_count, rendered_title, rendered_body, rendered_preview,
                    rendered_locale, template_version, render_status, deep_link_snapshot,
                    params, priority, state, change_sequence, created_at, last_event_at,
                    purge_after
                )
                SELECT ?, 'finance.invoice.overdue', 'financial', ?, ?, 1,
                       'Load row ' || series_no, 'Body', 'Preview', 'en', 1, 'ok',
                       'valueinsoft://marketing/campaigns/1', '{}'::jsonb, 'normal',
                       'unseen', nextval('c_1095.notification_feed_change_seq'),
                       TIMESTAMPTZ '2025-01-01 00:00:00+00'
                           + series_no * INTERVAL '1 second',
                       TIMESTAMPTZ '2025-01-01 00:00:00+00'
                           + series_no * INTERVAL '1 second',
                       NOW() + INTERVAL '180 days'
                FROM generate_series(1, 50000) AS rows(series_no)
                """, userId, loadEventId, loadEventId);

        NotificationCursorCodec.Cursor loadCursor = null;
        HashSet<UUID> seenRecipientUuids = new HashSet<>(50_000);
        boolean insertedWhilePaging = false;
        long loadStartedAt = System.nanoTime();
        while (true) {
            var rawPage = dbFeed.page(
                    companyId, userId, loadCursor, null, null, null, 101);
            int accepted = Math.min(100, rawPage.size());
            for (int index = 0; index < accepted; index++) {
                assertThat(seenRecipientUuids.add(rawPage.get(index).recipientUuid()))
                        .as("keyset pagination must not return duplicates")
                        .isTrue();
            }
            if (!insertedWhilePaging) {
                jdbc.update("""
                        INSERT INTO c_1095.notification_recipient (
                            user_id, type_key, category, first_event_id, latest_event_id,
                            aggregate_count, rendered_title, rendered_body, rendered_preview,
                            rendered_locale, template_version, render_status, deep_link_snapshot,
                            params, priority, state, change_sequence, created_at, last_event_at,
                            purge_after
                        )
                        SELECT ?, 'finance.invoice.overdue', 'financial', ?, ?, 1,
                               'Concurrent row ' || series_no, 'Body', 'Preview', 'en', 1, 'ok',
                               'valueinsoft://marketing/campaigns/1', '{}'::jsonb, 'normal',
                               'unseen', nextval('c_1095.notification_feed_change_seq'),
                               NOW() + series_no * INTERVAL '1 microsecond',
                               NOW() + series_no * INTERVAL '1 microsecond',
                               NOW() + INTERVAL '180 days'
                        FROM generate_series(1, 100) AS rows(series_no)
                        """, userId, loadEventId, loadEventId);
                insertedWhilePaging = true;
            }
            if (rawPage.size() <= 100) {
                break;
            }
            var last = rawPage.get(99);
            long lastRecipientId = dbFeed.locklessRecipientId(
                    companyId, userId, last.recipientUuid());
            loadCursor = new NotificationCursorCodec.Cursor(
                    last.lastEventAt(), lastRecipientId);
        }
        long loadElapsedMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - loadStartedAt);
        assertThat(seenRecipientUuids).hasSize(50_000);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(50_100);
        assertThat(loadElapsedMs)
                .as("50k rows should traverse within the two-minute external-test budget")
                .isLessThan(120_000);

        jdbc.update("""
                INSERT INTO public.tenant_user_grant_overrides (
                    tenant_id, user_id, capability_key, grant_mode,
                    scope_type, reason, source
                ) VALUES (?, ?, 'finance.entry.read', 'deny',
                          'company', 'Phase 2 visibility test', 'admin')
                ON CONFLICT DO NOTHING
                """, companyId, userId);
        assertThat(feed.page(companyId, userId, null, null, null, null, 100).items())
                .as("revoked capability must hide rows already materialized")
                .isEmpty();
    }

    @Test
    void aggregateCounterMatchesLineageFor200RandomConcurrentEvents() throws Exception {
        int companyId = 1095;
        int userCount = 7;
        int eventCount = 200;
        int groupCount = 20;
        String databaseUrl = requireDisposableDatabaseUrl();
        String databaseUser = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_USER", "postgres");
        String databasePassword = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_PASSWORD", "");
        migrate(databaseUrl, databaseUser, databasePassword);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                databaseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO public."Company" (id, "companyName", currency)
                VALUES (?, 'Phase 2 Randomised Co', 'EGP')
                ON CONFLICT (id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.tenants
                    (tenant_id, package_id, template_id, status, bootstrap_source)
                VALUES (?, 'enterprise', 'general_business', 'active', 'bootstrap')
                ON CONFLICT (tenant_id) DO NOTHING
                """, companyId);
        jdbc.update("DELETE FROM public.tenant_role_assignments WHERE tenant_id = ?",
                companyId);
        for (int offset = 1; offset <= userCount; offset++) {
            int userId = companyId * 100 + offset;
            jdbc.update("""
                    INSERT INTO public.users
                        (id, "userName", "userPassword", "userRole", "creationTime")
                    VALUES (?, ?, 'x', 'Owner', NOW())
                    ON CONFLICT (id) DO NOTHING
                    """, userId, "phase2_random_" + offset);
            jdbc.update("""
                    INSERT INTO public.tenant_role_assignments
                        (tenant_id, user_id, role_id, status, source, scope_type)
                    VALUES (?, ?, 'Owner', 'active', 'bootstrap', 'company')
                    ON CONFLICT DO NOTHING
                    """, companyId, userId);
        }
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS c_" + companyId);
        jdbc.execute("SELECT public.notification_bootstrap_tenant('c_" + companyId + "')");
        jdbc.update("DELETE FROM c_1095.notification_fanout_job");
        jdbc.update("DELETE FROM c_1095.notification_recipient_event");
        jdbc.update("DELETE FROM c_1095.notification_feed_change");
        jdbc.update("DELETE FROM c_1095.notification_recipient_audit");
        jdbc.update("DELETE FROM c_1095.notification_recipient");
        jdbc.update("DELETE FROM c_1095.notification_event");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CanonicalJsonService canonical = new CanonicalJsonService(mapper);
        DbNotificationEvent events = new DbNotificationEvent(jdbc, canonical, mapper);
        DbNotificationFanOutJob jobs = new DbNotificationFanOutJob(jdbc);
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.getFanOut().setSingleBatchThreshold(100);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gate.isEnabled(NotificationComponent.PUBLISH)).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gate);
        NotificationPublisher publisher = new NotificationPublisher(
                properties, provider, new NotificationIdempotencyService(canonical),
                events, jobs);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate tx = new TransactionTemplate(manager);
        NotificationFanOutService fanOut = fanOut(
                jdbc, mapper, canonical, properties, events, jobs, manager);

        List<Integer> randomGroups = new ArrayList<>(eventCount);
        for (int groupId = 1; groupId <= groupCount; groupId++) {
            randomGroups.add(groupId);
        }
        Random random = new Random(20260726L);
        while (randomGroups.size() < eventCount) {
            randomGroups.add(random.nextInt(groupCount) + 1);
        }
        Collections.shuffle(randomGroups, random);
        for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
            int groupId = randomGroups.get(eventIndex);
            int sequence = eventIndex;
            tx.executeWithoutResult(status -> publisher.publish(request(
                    companyId,
                    "phase2:random:" + sequence,
                    "Random campaign " + sequence,
                    groupId)));
        }

        List<NotificationFanOutJob> claimedJobs = new ArrayList<>(eventCount);
        for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
            claimedJobs.add(fanOut.claimAndDecide(
                    companyId, "random-claim-" + eventIndex).orElseThrow());
        }
        Collections.shuffle(claimedJobs, random);

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = claimedJobs.stream()
                    .map(job -> executor.submit(() -> {
                        start.await();
                        fanOut.materialize(job);
                        return null;
                    }))
                    .toList();
            start.countDown();
            for (var future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        }

        int expectedEffects = eventCount * userCount;
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient",
                Integer.class)).isEqualTo(groupCount * userCount);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isEqualTo(expectedEffects);
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(aggregate_count), 0)
                FROM c_1095.notification_recipient
                """, Integer.class)).isEqualTo(expectedEffects);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient recipient
                WHERE recipient.aggregate_count <> (
                    SELECT COUNT(*)
                    FROM c_1095.notification_recipient_event lineage
                    WHERE lineage.recipient_id = recipient.recipient_id
                )
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient recipient
                WHERE recipient.latest_event_id <> (
                    SELECT lineage.event_id
                    FROM c_1095.notification_recipient_event lineage
                    JOIN c_1095.notification_event event
                      ON event.event_id = lineage.event_id
                    WHERE lineage.recipient_id = recipient.recipient_id
                    ORDER BY event.created_at DESC, event.event_id DESC
                    LIMIT 1
                )
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_feed_change",
                Integer.class)).isEqualTo(expectedEffects);
    }

    @Test
    void aggregationStateArchiveAndWindowTransitionsRemainConsistent() {
        int companyId = 1095;
        String databaseUrl = requireDisposableDatabaseUrl();
        String databaseUser = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_USER", "postgres");
        String databasePassword = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_PASSWORD", "");
        migrate(databaseUrl, databaseUser, databasePassword);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                databaseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO public."Company" (id, "companyName", currency)
                VALUES (?, 'Phase 2 Lifecycle Co', 'EGP')
                ON CONFLICT (id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.tenants
                    (tenant_id, package_id, template_id, status, bootstrap_source)
                VALUES (?, 'enterprise', 'general_business', 'active', 'bootstrap')
                ON CONFLICT (tenant_id) DO NOTHING
                """, companyId);
        jdbc.update("DELETE FROM public.tenant_role_assignments WHERE tenant_id = ?",
                companyId);
        for (int offset = 1; offset <= 7; offset++) {
            int userId = companyId * 100 + offset;
            jdbc.update("""
                    INSERT INTO public.users
                        (id, "userName", "userPassword", "userRole", "creationTime")
                    VALUES (?, ?, 'x', 'Owner', NOW())
                    ON CONFLICT (id) DO NOTHING
                    """, userId, "phase2_lifecycle_" + offset);
            jdbc.update("""
                    INSERT INTO public.tenant_role_assignments
                        (tenant_id, user_id, role_id, status, source, scope_type)
                    VALUES (?, ?, 'Owner', 'active', 'bootstrap', 'company')
                    ON CONFLICT DO NOTHING
                    """, companyId, userId);
        }
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS c_" + companyId);
        jdbc.execute("SELECT public.notification_bootstrap_tenant('c_" + companyId + "')");
        jdbc.update("DELETE FROM c_1095.notification_fanout_job");
        jdbc.update("DELETE FROM c_1095.notification_recipient_event");
        jdbc.update("DELETE FROM c_1095.notification_feed_change");
        jdbc.update("DELETE FROM c_1095.notification_recipient_audit");
        jdbc.update("DELETE FROM c_1095.notification_recipient");
        jdbc.update("DELETE FROM c_1095.notification_event");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CanonicalJsonService canonical = new CanonicalJsonService(mapper);
        DbNotificationEvent events = new DbNotificationEvent(jdbc, canonical, mapper);
        DbNotificationFanOutJob jobs = new DbNotificationFanOutJob(jdbc);
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gate.isEnabled(NotificationComponent.PUBLISH)).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gate);
        NotificationPublisher publisher = new NotificationPublisher(
                properties, provider, new NotificationIdempotencyService(canonical),
                events, jobs);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate tx = new TransactionTemplate(manager);
        NotificationFanOutService fanOut = fanOut(
                jdbc, mapper, canonical, properties, events, jobs, manager);
        DbNotificationFeed dbFeed = new DbNotificationFeed(jdbc, mapper);
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        NotificationSummaryService summaries = new NotificationSummaryService(
                dbFeed, new NotificationAudienceResolver(jdbc), redis, mapper);
        NotificationFeedService feed = new NotificationFeedService(
                dbFeed, new DbNotificationFeedChange(jdbc),
                new DbNotificationRecipient(jdbc, canonical),
                new NotificationAudienceResolver(jdbc), new NotificationCursorCodec(),
                summaries);
        int userId = companyId * 100 + 1;

        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:lifecycle:read:1", "Read lifecycle one", 1)));
        fanOut.materialize(fanOut.claimAndDecide(
                companyId, "lifecycle-read-1").orElseThrow());
        UUID readRecipient = jdbc.queryForObject("""
                SELECT recipient_uuid FROM c_1095.notification_recipient
                WHERE user_id = ? AND group_key = 'campaign:1'
                  AND group_closed_at IS NULL
                """, UUID.class, userId);
        feed.markRead(companyId, userId, readRecipient, "web");
        assertThat(jdbc.queryForObject("""
                SELECT state FROM c_1095.notification_recipient
                WHERE recipient_uuid = ?
                """, String.class, readRecipient)).isEqualTo("read");

        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:lifecycle:read:2", "Read lifecycle two", 1)));
        fanOut.materialize(fanOut.claimAndDecide(
                companyId, "lifecycle-read-2").orElseThrow());
        assertThat(jdbc.queryForMap("""
                SELECT state, seen_at, read_at, aggregate_count
                FROM c_1095.notification_recipient
                WHERE recipient_uuid = ?
                """, readRecipient))
                .containsEntry("state", "unseen")
                .containsEntry("aggregate_count", 2)
                .containsEntry("seen_at", null)
                .containsEntry("read_at", null);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient_audit
                WHERE recipient_id = (
                    SELECT recipient_id FROM c_1095.notification_recipient
                    WHERE recipient_uuid = ?
                ) AND to_state = 'read'
                """, Integer.class, readRecipient)).isOne();

        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:lifecycle:archive:1", "Archive lifecycle one", 2)));
        fanOut.materialize(fanOut.claimAndDecide(
                companyId, "lifecycle-archive-1").orElseThrow());
        UUID archivedRecipient = jdbc.queryForObject("""
                SELECT recipient_uuid FROM c_1095.notification_recipient
                WHERE user_id = ? AND group_key = 'campaign:2'
                  AND group_closed_at IS NULL
                """, UUID.class, userId);
        feed.archive(companyId, userId, archivedRecipient, "web");
        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:lifecycle:archive:2", "Archive lifecycle two", 2)));
        fanOut.materialize(fanOut.claimAndDecide(
                companyId, "lifecycle-archive-2").orElseThrow());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient
                WHERE user_id = ? AND group_key = 'campaign:2'
                """, Integer.class, userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT archived_at IS NOT NULL AND group_closed_at IS NOT NULL
                FROM c_1095.notification_recipient
                WHERE recipient_uuid = ?
                """, Boolean.class, archivedRecipient)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient
                WHERE user_id = ? AND group_key = 'campaign:2'
                  AND archived_at IS NULL AND group_closed_at IS NULL
                """, Integer.class, userId)).isOne();

        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:lifecycle:window:1", "Window lifecycle one", 3)));
        fanOut.materialize(fanOut.claimAndDecide(
                companyId, "lifecycle-window-1").orElseThrow());
        long expiredRecipientId = jdbc.queryForObject("""
                SELECT recipient_id FROM c_1095.notification_recipient
                WHERE user_id = ? AND group_key = 'campaign:3'
                  AND group_closed_at IS NULL
                """, Long.class, userId);
        jdbc.update("""
                UPDATE c_1095.notification_recipient
                SET last_event_at = NOW() - INTERVAL '2 days'
                WHERE group_key = 'campaign:3'
                """);
        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:lifecycle:window:2", "Window lifecycle two", 3)));
        fanOut.materialize(fanOut.claimAndDecide(
                companyId, "lifecycle-window-2").orElseThrow());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient
                WHERE user_id = ? AND group_key = 'campaign:3'
                """, Integer.class, userId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT group_closed_at IS NOT NULL
                FROM c_1095.notification_recipient
                WHERE recipient_id = ?
                """, Boolean.class, expiredRecipientId)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient
                WHERE user_id = ? AND group_key = 'campaign:3'
                  AND group_closed_at IS NULL
                """, Integer.class, userId)).isOne();
    }

    @Test
    void fanOutScalesFromSingleBatchToFiveCursorBatches() {
        int companyId = 1095;
        int userIdBase = companyId * 1_000;
        String databaseUrl = requireDisposableDatabaseUrl();
        String databaseUser = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_USER", "postgres");
        String databasePassword = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_PASSWORD", "");
        migrate(databaseUrl, databaseUser, databasePassword);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                databaseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO public."Company" (id, "companyName", currency)
                VALUES (?, 'Phase 2 Scale Co', 'EGP')
                ON CONFLICT (id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.tenants
                    (tenant_id, package_id, template_id, status, bootstrap_source)
                VALUES (?, 'enterprise', 'general_business', 'active', 'bootstrap')
                ON CONFLICT (tenant_id) DO NOTHING
                """, companyId);
        jdbc.update("DELETE FROM public.tenant_role_assignments WHERE tenant_id = ?",
                companyId);
        insertScaleUsers(jdbc, companyId, userIdBase, 1, 100);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS c_" + companyId);
        jdbc.execute("SELECT public.notification_bootstrap_tenant('c_" + companyId + "')");
        jdbc.update("DELETE FROM c_1095.notification_fanout_job");
        jdbc.update("DELETE FROM c_1095.notification_recipient_event");
        jdbc.update("DELETE FROM c_1095.notification_feed_change");
        jdbc.update("DELETE FROM c_1095.notification_recipient_audit");
        jdbc.update("DELETE FROM c_1095.notification_recipient");
        jdbc.update("DELETE FROM c_1095.notification_event");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CanonicalJsonService canonical = new CanonicalJsonService(mapper);
        DbNotificationEvent events = new DbNotificationEvent(jdbc, canonical, mapper);
        DbNotificationFanOutJob jobs = new DbNotificationFanOutJob(jdbc);
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.getFanOut().setSingleBatchThreshold(500);
        properties.getFanOut().setBatchSize(500);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gate.isEnabled(NotificationComponent.PUBLISH)).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gate);
        NotificationPublisher publisher = new NotificationPublisher(
                properties, provider, new NotificationIdempotencyService(canonical),
                events, jobs);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate tx = new TransactionTemplate(manager);
        NotificationFanOutService fanOut = fanOut(
                jdbc, mapper, canonical, properties, events, jobs, manager);

        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:scale:single", "Single batch scale", 1)));
        NotificationFanOutJob singleJob = fanOut.claimAndDecide(
                companyId, "scale-single").orElseThrow();
        assertThat(singleJob.mode()).isEqualTo(NotificationFanOutService.SINGLE);
        assertThat(singleJob.boundedAudience()).isEqualTo(100);
        fanOut.materialize(singleJob);
        assertThat(jdbc.queryForMap("""
                SELECT status, batches_processed, recipients_created
                FROM c_1095.notification_fanout_job
                WHERE job_id = ?
                """, singleJob.jobId()))
                .containsEntry("status", "completed")
                .containsEntry("batches_processed", 1)
                .containsEntry("recipients_created", 100);

        insertScaleUsers(jdbc, companyId, userIdBase, 101, 2_300);
        tx.executeWithoutResult(status -> publisher.publish(request(
                companyId, "phase2:scale:cursor", "Cursor batch scale", 2)));
        NotificationFanOutJob cursorJob = fanOut.claimAndDecide(
                companyId, "scale-cursor").orElseThrow();
        assertThat(cursorJob.mode()).isEqualTo(NotificationFanOutService.CURSOR);
        assertThat(cursorJob.boundedAudience())
                .as("bounded probe must stop at threshold plus one")
                .isEqualTo(501);
        fanOut.materialize(cursorJob);

        assertThat(jdbc.queryForMap("""
                SELECT status, batches_processed, recipients_created, fanout_cursor
                FROM c_1095.notification_fanout_job
                WHERE job_id = ?
                """, cursorJob.jobId()))
                .containsEntry("status", "completed")
                .containsEntry("batches_processed", 5)
                .containsEntry("recipients_created", 2_300)
                .containsEntry("fanout_cursor", userIdBase + 2_300);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient
                WHERE group_key = 'campaign:1'
                """, Integer.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient
                WHERE group_key = 'campaign:2'
                """, Integer.class)).isEqualTo(2_300);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isEqualTo(2_400);
    }

    @Test
    void crashAfterLineageInsertRollsBackAndReplayIsExactlyOnce() {
        Phase2Harness harness = singleUserHarness();
        var published = harness.tx().execute(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:crash:lineage", "Crash replay", 1)));
        assertThat(published).isNotNull();
        var event = harness.events().require(harness.companyId(), published.eventId());
        var catalog = new DbNotificationCatalog(harness.jdbc())
                .requireActive(event.typeKey());
        var rendered = new NotificationTemplateRenderer(
                new DbNotificationTemplate(harness.jdbc()),
                new NotificationPreviewRenderer()).render(event, catalog, "en");
        DbNotificationRecipient recipients =
                new DbNotificationRecipient(harness.jdbc(), harness.canonical());
        DbNotificationFeedChange changes = new DbNotificationFeedChange(harness.jdbc());
        DbNotificationRecipientEvent crashingLineage =
                new DbNotificationRecipientEvent(harness.jdbc()) {
                    private boolean crash = true;

                    @Override
                    public boolean insert(long companyId, long eventId, int userId,
                                          long recipientId, int sequence) {
                        boolean inserted = super.insert(
                                companyId, eventId, userId, recipientId, sequence);
                        if (inserted && crash) {
                            crash = false;
                            throw new SimulatedAggregationCrash();
                        }
                        return inserted;
                    }
                };
        NotificationAggregationService crashingAggregation =
                new NotificationAggregationService(recipients, crashingLineage, changes);

        assertThatThrownBy(() -> harness.tx().executeWithoutResult(status ->
                crashingAggregation.apply(
                        harness.companyId(), harness.userId(), event, catalog, rendered)))
                .isInstanceOf(SimulatedAggregationCrash.class);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient",
                Integer.class)).isZero();
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isZero();
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_feed_change",
                Integer.class)).isZero();

        NotificationAggregationService aggregation = new NotificationAggregationService(
                recipients, new DbNotificationRecipientEvent(harness.jdbc()), changes);
        var replay = harness.tx().execute(status -> aggregation.apply(
                harness.companyId(), harness.userId(), event, catalog, rendered));
        assertThat(replay).isNotNull();
        assertThat(replay.created()).isTrue();
        Map<String, Object> snapshot = harness.jdbc().queryForMap("""
                SELECT aggregate_count, latest_event_id, rendered_title, rendered_body,
                       rendered_preview, rendered_locale, template_version, render_status,
                       deep_link_snapshot, params::text AS params, state, change_sequence,
                       last_event_at, purge_after
                FROM c_1095.notification_recipient
                WHERE user_id = ?
                """, harness.userId());

        var duplicate = harness.tx().execute(status -> aggregation.apply(
                harness.companyId(), harness.userId(), event, catalog, rendered));
        assertThat(duplicate).isNotNull();
        assertThat(duplicate.alreadyApplied()).isTrue();
        assertThat(harness.jdbc().queryForMap("""
                SELECT aggregate_count, latest_event_id, rendered_title, rendered_body,
                       rendered_preview, rendered_locale, template_version, render_status,
                       deep_link_snapshot, params::text AS params, state, change_sequence,
                       last_event_at, purge_after
                FROM c_1095.notification_recipient
                WHERE user_id = ?
                """, harness.userId())).isEqualTo(snapshot);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isOne();
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_feed_change",
                Integer.class)).isOne();
    }

    @Test
    void retentionDoesNotDeleteExpiredEventReferencedByActiveAggregate() {
        Phase2Harness harness = singleUserHarness();
        var referenced = harness.tx().execute(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:retention:referenced",
                "Referenced expired event", 1)));
        assertThat(referenced).isNotNull();
        NotificationFanOutService fanOut = fanOut(
                harness.jdbc(), harness.mapper(), harness.canonical(), harness.properties(),
                harness.events(), harness.jobs(), harness.manager());
        fanOut.materialize(fanOut.claimAndDecide(
                harness.companyId(), "retention-referenced").orElseThrow());
        harness.jdbc().update(
                "DELETE FROM c_1095.notification_fanout_job WHERE event_id = ?",
                referenced.eventId());
        harness.jdbc().update("""
                UPDATE c_1095.notification_event
                SET created_at = NOW() - INTERVAL '200 days',
                    expires_at = NOW() - INTERVAL '110 days'
                WHERE event_id = ?
                """, referenced.eventId());

        DbNotificationRetention retention =
                new DbNotificationRetention(harness.jdbc());
        assertThat(retention.purgeUnreferencedEvents(
                harness.companyId(), 100)).isZero();
        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_event
                WHERE event_id = ?
                """, Integer.class, referenced.eventId())).isOne();

        var unreferenced = harness.tx().execute(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:retention:unreferenced",
                "Unreferenced expired event", 2)));
        assertThat(unreferenced).isNotNull();
        harness.jdbc().update(
                "DELETE FROM c_1095.notification_fanout_job WHERE event_id = ?",
                unreferenced.eventId());
        harness.jdbc().update("""
                UPDATE c_1095.notification_event
                SET created_at = NOW() - INTERVAL '200 days',
                    expires_at = NOW() - INTERVAL '110 days'
                WHERE event_id = ?
                """, unreferenced.eventId());

        assertThat(retention.purgeUnreferencedEvents(
                harness.companyId(), 100)).isOne();
        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_event
                WHERE event_id = ?
                """, Integer.class, referenced.eventId())).isOne();
        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_event
                WHERE event_id = ?
                """, Integer.class, unreferenced.eventId())).isZero();
    }

    @Test
    void probeTimeoutSelectsCursorModeInFreshTransaction() {
        Phase2Harness harness = singleUserHarness();
        harness.tx().executeWithoutResult(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:probe:timeout", "Probe timeout", 1)));
        NotificationAudienceResolver timeoutAudience =
                new NotificationAudienceResolver(harness.jdbc()) {
                    @Override
                    public int countBounded(long companyId, Integer branchId,
                                            String requiredCapability, int limit) {
                        throw new DataAccessResourceFailureException(
                                "simulated PostgreSQL statement timeout",
                                new SQLException("canceling statement due to statement timeout",
                                        "57014"));
                    }
                };
        NotificationAggregationService aggregation = new NotificationAggregationService(
                new DbNotificationRecipient(harness.jdbc(), harness.canonical()),
                new DbNotificationRecipientEvent(harness.jdbc()),
                new DbNotificationFeedChange(harness.jdbc()));
        NotificationFanOutService fanOut = new NotificationFanOutService(
                harness.jdbc(), harness.properties(), harness.jobs(), harness.events(),
                new DbNotificationCatalog(harness.jdbc()), timeoutAudience,
                new NotificationTemplateRenderer(
                        new DbNotificationTemplate(harness.jdbc()),
                        new NotificationPreviewRenderer()),
                aggregation, harness.manager());

        NotificationFanOutJob claimed = fanOut.claimAndDecide(
                harness.companyId(), "probe-timeout").orElseThrow();
        assertThat(claimed.mode()).isEqualTo(NotificationFanOutService.CURSOR);
        assertThat(claimed.boundedAudience()).isEqualTo(
                harness.properties().getFanOut().getSingleBatchThreshold() + 1);
        assertThat(harness.jdbc().queryForMap("""
                SELECT mode, bounded_audience, status, attempt_count
                FROM c_1095.notification_fanout_job
                WHERE job_id = ?
                """, claimed.jobId()))
                .containsEntry("mode", NotificationFanOutService.CURSOR)
                .containsEntry("bounded_audience",
                        harness.properties().getFanOut().getSingleBatchThreshold() + 1)
                .containsEntry("status", "claimed")
                .containsEntry("attempt_count", 1);
    }

    @Test
    void twoDistinctEventsRaceNoOpenItemRetriesOnceAndRecordsMetric() throws Exception {
        Phase2Harness harness = singleUserHarness();
        harness.tx().executeWithoutResult(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:conflict:one", "Conflict one", 1)));
        harness.tx().executeWithoutResult(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:conflict:two", "Conflict two", 1)));

        CyclicBarrier emptyGroupBarrier = new CyclicBarrier(2);
        DbNotificationRecipient racingRecipients =
                new DbNotificationRecipient(harness.jdbc(), harness.canonical()) {
                    @Override
                    public java.util.Optional<OpenRecipient> lockOpen(
                            long companyId, int userId, String groupKey) {
                        java.util.Optional<OpenRecipient> result =
                                super.lockOpen(companyId, userId, groupKey);
                        if (result.isEmpty()) {
                            try {
                                emptyGroupBarrier.await(10, TimeUnit.SECONDS);
                            } catch (Exception exception) {
                                throw new IllegalStateException(
                                        "Could not synchronize open-group race", exception);
                            }
                        }
                        return result;
                    }
                };
        NotificationAggregationService aggregation = new NotificationAggregationService(
                racingRecipients, new DbNotificationRecipientEvent(harness.jdbc()),
                new DbNotificationFeedChange(harness.jdbc()));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        NotificationFanOutService fanOut = new NotificationFanOutService(
                harness.jdbc(), harness.properties(), harness.jobs(), harness.events(),
                new DbNotificationCatalog(harness.jdbc()),
                new NotificationAudienceResolver(harness.jdbc()),
                new NotificationTemplateRenderer(
                        new DbNotificationTemplate(harness.jdbc()),
                        new NotificationPreviewRenderer()),
                aggregation, harness.manager(), metrics);
        NotificationFanOutJob first = fanOut.claimAndDecide(
                harness.companyId(), "conflict-one").orElseThrow();
        NotificationFanOutJob second = fanOut.claimAndDecide(
                harness.companyId(), "conflict-two").orElseThrow();

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstWorker = executor.submit(() -> {
                start.await();
                fanOut.materialize(first);
                return null;
            });
            var secondWorker = executor.submit(() -> {
                start.await();
                fanOut.materialize(second);
                return null;
            });
            start.countDown();
            firstWorker.get(30, TimeUnit.SECONDS);
            secondWorker.get(30, TimeUnit.SECONDS);
        }

        assertThat(metrics.get("notification.fanout.conflict_retry")
                .counter().count()).isEqualTo(1.0);
        assertThat(harness.jdbc().queryForMap("""
                SELECT COUNT(*) AS recipient_count,
                       COALESCE(SUM(aggregate_count), 0) AS aggregate_count
                FROM c_1095.notification_recipient
                """))
                .containsEntry("recipient_count", 1L)
                .containsEntry("aggregate_count", 2L);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isEqualTo(2);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_feed_change",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void cursorBatchRollbackBeforeAdvanceReplaysWithoutDuplicateEffects() {
        Phase2Harness harness = singleUserHarness();
        int userIdBase = configureContiguousAudience(harness, 5);
        harness.properties().getFanOut().setSingleBatchThreshold(1);
        harness.properties().getFanOut().setBatchSize(2);
        harness.tx().executeWithoutResult(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:cursor:rollback", "Cursor rollback", 1)));
        DbNotificationFanOutJob failBeforeAdvance =
                new DbNotificationFanOutJob(harness.jdbc()) {
                    private boolean fail = true;

                    @Override
                    public void advance(NotificationFanOutJob job, int cursor,
                                        int created, boolean complete) {
                        if (fail) {
                            fail = false;
                            throw new SimulatedCursorCrash();
                        }
                        super.advance(job, cursor, created, complete);
                    }
                };
        NotificationFanOutService fanOut = fanOut(
                harness.jdbc(), harness.mapper(), harness.canonical(), harness.properties(),
                harness.events(), failBeforeAdvance, harness.manager());
        NotificationFanOutJob job = fanOut.claimAndDecide(
                harness.companyId(), "cursor-before-advance").orElseThrow();

        assertThatThrownBy(() -> fanOut.materializeNextBatch(job))
                .isInstanceOf(SimulatedCursorCrash.class);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient",
                Integer.class)).isZero();
        assertThat(harness.jdbc().queryForMap("""
                SELECT status, fanout_cursor
                FROM c_1095.notification_fanout_job
                WHERE job_id = ?
                """, job.jobId()))
                .containsEntry("status", "failed")
                .containsEntry("fanout_cursor", null);

        harness.jdbc().update("""
                UPDATE c_1095.notification_fanout_job
                SET next_attempt_at = NOW()
                WHERE job_id = ?
                """, job.jobId());
        NotificationFanOutJob reclaimed = fanOut.claimAndDecide(
                harness.companyId(), "cursor-before-advance-retry").orElseThrow();
        assertThat(reclaimed.jobId()).isEqualTo(job.jobId());
        assertThat(reclaimed.fanoutCursor()).isNull();
        fanOut.materialize(reclaimed);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient",
                Integer.class)).isEqualTo(5);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isEqualTo(5);
        assertThat(harness.jdbc().queryForObject("""
                SELECT fanout_cursor FROM c_1095.notification_fanout_job
                WHERE job_id = ?
                """, Integer.class, job.jobId())).isEqualTo(userIdBase + 5);
    }

    @Test
    void cursorBatchResumesFromCommittedCursorAfterProcessStops() {
        Phase2Harness harness = singleUserHarness();
        int userIdBase = configureContiguousAudience(harness, 5);
        harness.properties().getFanOut().setSingleBatchThreshold(1);
        harness.properties().getFanOut().setBatchSize(2);
        harness.tx().executeWithoutResult(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:cursor:resume", "Cursor resume", 1)));
        NotificationFanOutService fanOut = fanOut(
                harness.jdbc(), harness.mapper(), harness.canonical(), harness.properties(),
                harness.events(), harness.jobs(), harness.manager());
        NotificationFanOutJob job = fanOut.claimAndDecide(
                harness.companyId(), "cursor-after-commit").orElseThrow();

        NotificationFanOutService.BatchProgress committed =
                fanOut.materializeNextBatch(job);
        assertThat(committed.complete()).isFalse();
        assertThat(committed.cursor()).isEqualTo(userIdBase + 2);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient",
                Integer.class)).isEqualTo(2);
        assertThat(harness.jdbc().queryForObject("""
                SELECT fanout_cursor FROM c_1095.notification_fanout_job
                WHERE job_id = ?
                """, Integer.class, job.jobId())).isEqualTo(committed.cursor());

        NotificationFanOutJob resumed = new NotificationFanOutJob(
                job.companyId(), job.jobId(), job.eventId(), job.mode(),
                job.boundedAudience(), committed.cursor(), job.attemptCount());
        fanOut.materialize(resumed);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient",
                Integer.class)).isEqualTo(5);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isEqualTo(5);
        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT event_id, user_id
                    FROM c_1095.notification_recipient_event
                    GROUP BY event_id, user_id
                    HAVING COUNT(*) > 1
                ) duplicates
                """, Integer.class)).isZero();
    }

    @Test
    void cursorBatchUsesLiveMembershipAtEachCommittedBoundary() {
        Phase2Harness harness = singleUserHarness();
        int userIdBase = harness.companyId() * 1_000;
        harness.jdbc().update(
                "DELETE FROM public.tenant_role_assignments WHERE tenant_id = ?",
                harness.companyId());
        insertScaleUsers(harness.jdbc(), harness.companyId(), userIdBase, 1, 14);
        harness.jdbc().update("""
                DELETE FROM public.tenant_role_assignments
                WHERE tenant_id = ?
                  AND user_id NOT IN (?, ?, ?, ?, ?, ?)
                """, harness.companyId(),
                userIdBase + 2, userIdBase + 4, userIdBase + 6,
                userIdBase + 8, userIdBase + 10, userIdBase + 12);
        harness.properties().getFanOut().setSingleBatchThreshold(1);
        harness.properties().getFanOut().setBatchSize(2);
        harness.tx().executeWithoutResult(status -> harness.publisher().publish(request(
                harness.companyId(), "phase2:cursor:membership",
                "Cursor membership", 1)));
        NotificationFanOutService fanOut = fanOut(
                harness.jdbc(), harness.mapper(), harness.canonical(), harness.properties(),
                harness.events(), harness.jobs(), harness.manager());
        NotificationFanOutJob job = fanOut.claimAndDecide(
                harness.companyId(), "cursor-membership").orElseThrow();

        NotificationFanOutService.BatchProgress firstBatch =
                fanOut.materializeNextBatch(job);
        assertThat(firstBatch.cursor()).isEqualTo(userIdBase + 4);
        harness.jdbc().update("""
                INSERT INTO public.tenant_role_assignments
                    (tenant_id, user_id, role_id, status, source, scope_type)
                VALUES (?, ?, 'Owner', 'active', 'bootstrap', 'company'),
                       (?, ?, 'Owner', 'active', 'bootstrap', 'company')
                ON CONFLICT DO NOTHING
                """, harness.companyId(), userIdBase + 3,
                harness.companyId(), userIdBase + 14);
        harness.jdbc().update("""
                UPDATE public.tenant_role_assignments
                SET status = 'inactive'
                WHERE tenant_id = ? AND user_id = ?
                """, harness.companyId(), userIdBase + 10);

        NotificationFanOutJob resumed = new NotificationFanOutJob(
                job.companyId(), job.jobId(), job.eventId(), job.mode(),
                job.boundedAudience(), firstBatch.cursor(), job.attemptCount());
        fanOut.materialize(resumed);
        assertThat(harness.jdbc().queryForList("""
                SELECT user_id FROM c_1095.notification_recipient
                ORDER BY user_id
                """, Integer.class)).containsExactly(
                userIdBase + 2, userIdBase + 4, userIdBase + 6,
                userIdBase + 8, userIdBase + 12, userIdBase + 14);
        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient
                WHERE user_id IN (?, ?)
                """, Integer.class, userIdBase + 3, userIdBase + 10)).isZero();
    }

    @Test
    void twoIndependentWorkerInstancesDrainSharedQueueWithoutDrift() throws Exception {
        Phase2Harness harness = singleUserHarness();
        configureContiguousAudience(harness, 20);
        int eventCount = 100;
        int groupCount = 10;
        for (int eventIndex = 0; eventIndex < eventCount; eventIndex++) {
            int sequence = eventIndex;
            harness.tx().executeWithoutResult(status -> harness.publisher().publish(request(
                    harness.companyId(),
                    "phase2:multi-instance:" + sequence,
                    "Multi-instance event " + sequence,
                    (sequence % groupCount) + 1)));
        }

        String databaseUrl = requireDisposableDatabaseUrl();
        String databaseUser = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_USER", "postgres");
        String databasePassword = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_PASSWORD", "");
        NotificationFanOutService instanceOne = independentFanOut(
                databaseUrl, databaseUser, databasePassword);
        NotificationFanOutService instanceTwo = independentFanOut(
                databaseUrl, databaseUser, databasePassword);
        CountDownLatch start = new CountDownLatch(1);
        CyclicBarrier bothClaimed = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var workerOne = executor.submit(() -> drainQueue(
                    instanceOne, harness.companyId(), "multi-instance-one",
                    start, bothClaimed));
            var workerTwo = executor.submit(() -> drainQueue(
                    instanceTwo, harness.companyId(), "multi-instance-two",
                    start, bothClaimed));
            start.countDown();
            assertThat(workerOne.get(120, TimeUnit.SECONDS)
                    + workerTwo.get(120, TimeUnit.SECONDS)).isEqualTo(eventCount);
        }

        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_fanout_job
                WHERE status = 'completed'
                """, Integer.class)).isEqualTo(eventCount);
        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(DISTINCT claimed_by)
                FROM c_1095.notification_fanout_job
                WHERE status = 'completed'
                """, Integer.class)).isEqualTo(2);
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient_event",
                Integer.class)).isEqualTo(eventCount * 20);
        assertThat(harness.jdbc().queryForObject("""
                SELECT COUNT(*) FROM c_1095.notification_recipient recipient
                WHERE recipient.aggregate_count <> (
                    SELECT COUNT(*)
                    FROM c_1095.notification_recipient_event lineage
                    WHERE lineage.recipient_id = recipient.recipient_id
                )
                """, Integer.class)).isZero();
        assertThat(harness.jdbc().queryForObject(
                "SELECT COUNT(*) FROM c_1095.notification_recipient",
                Integer.class)).isEqualTo(groupCount * 20);
    }

    private static NotificationRequest request(int companyId, String key, String name) {
        return request(companyId, key, name, 1);
    }

    private static NotificationRequest request(
            int companyId, String key, String name, int campaignId) {
        return NotificationRequest.builder(
                        companyId, "marketing.campaign.published", key)
                .subject("campaign", (long) campaignId)
                .params(Map.of("campaignId", campaignId, "campaignName", name))
                .build();
    }

    private static void insertScaleUsers(
            JdbcTemplate jdbc, int companyId, int userIdBase, int from, int to) {
        jdbc.update("""
                INSERT INTO public.users
                    (id, "userName", "userPassword", "userRole", "creationTime")
                SELECT ? + series_no,
                       'phase2_scale_' || series_no,
                       'x', 'Owner', NOW()
                FROM generate_series(?, ?) AS rows(series_no)
                ON CONFLICT (id) DO NOTHING
                """, userIdBase, from, to);
        jdbc.update("""
                INSERT INTO public.tenant_role_assignments
                    (tenant_id, user_id, role_id, status, source, scope_type)
                SELECT ?, ? + series_no, 'Owner', 'active', 'bootstrap', 'company'
                FROM generate_series(?, ?) AS rows(series_no)
                ON CONFLICT DO NOTHING
                """, companyId, userIdBase, from, to);
    }

    private static int configureContiguousAudience(
            Phase2Harness harness, int userCount) {
        int userIdBase = harness.companyId() * 1_000;
        harness.jdbc().update(
                "DELETE FROM public.tenant_role_assignments WHERE tenant_id = ?",
                harness.companyId());
        insertScaleUsers(
                harness.jdbc(), harness.companyId(), userIdBase, 1, userCount);
        return userIdBase;
    }

    private static NotificationFanOutService independentFanOut(
            String databaseUrl, String databaseUser, String databasePassword) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                databaseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CanonicalJsonService canonical = new CanonicalJsonService(mapper);
        DbNotificationEvent events = new DbNotificationEvent(jdbc, canonical, mapper);
        DbNotificationFanOutJob jobs = new DbNotificationFanOutJob(jdbc);
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        properties.getFanOut().setSingleBatchThreshold(100);
        NotificationAggregationService aggregation = new NotificationAggregationService(
                new DbNotificationRecipient(jdbc, canonical),
                new DbNotificationRecipientEvent(jdbc),
                new DbNotificationFeedChange(jdbc));
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        return new NotificationFanOutService(
                jdbc, properties, jobs, events, new DbNotificationCatalog(jdbc),
                new NotificationAudienceResolver(jdbc),
                new NotificationTemplateRenderer(
                        new DbNotificationTemplate(jdbc),
                        new NotificationPreviewRenderer()),
                aggregation, manager, new SimpleMeterRegistry());
    }

    private static int drainQueue(
            NotificationFanOutService fanOut,
            int companyId,
            String workerId,
            CountDownLatch start,
            CyclicBarrier bothClaimed) throws Exception {
        start.await();
        NotificationFanOutJob first = fanOut.claimAndDecide(companyId, workerId)
                .orElseThrow();
        bothClaimed.await(30, TimeUnit.SECONDS);
        int processed = 0;
        NotificationFanOutJob current = first;
        while (current != null) {
            fanOut.materialize(current);
            processed++;
            current = fanOut.claimAndDecide(companyId, workerId).orElse(null);
        }
        return processed;
    }

    private static Phase2Harness singleUserHarness() {
        int companyId = 1095;
        int userId = companyId * 100 + 1;
        String databaseUrl = requireDisposableDatabaseUrl();
        String databaseUser = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_USER", "postgres");
        String databasePassword = System.getenv().getOrDefault(
                "VLS_NOTIFICATION_PHASE2_DB_PASSWORD", "");
        migrate(databaseUrl, databaseUser, databasePassword);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                databaseUrl, databaseUser, databasePassword);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO public."Company" (id, "companyName", currency)
                VALUES (?, 'Phase 2 Safety Co', 'EGP')
                ON CONFLICT (id) DO NOTHING
                """, companyId);
        jdbc.update("""
                INSERT INTO public.tenants
                    (tenant_id, package_id, template_id, status, bootstrap_source)
                VALUES (?, 'enterprise', 'general_business', 'active', 'bootstrap')
                ON CONFLICT (tenant_id) DO NOTHING
                """, companyId);
        jdbc.update("DELETE FROM public.tenant_role_assignments WHERE tenant_id = ?",
                companyId);
        jdbc.update("""
                INSERT INTO public.users
                    (id, "userName", "userPassword", "userRole", "creationTime")
                VALUES (?, 'phase2_safety', 'x', 'Owner', NOW())
                ON CONFLICT (id) DO NOTHING
                """, userId);
        jdbc.update("""
                INSERT INTO public.tenant_role_assignments
                    (tenant_id, user_id, role_id, status, source, scope_type)
                VALUES (?, ?, 'Owner', 'active', 'bootstrap', 'company')
                ON CONFLICT DO NOTHING
                """, companyId, userId);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS c_" + companyId);
        jdbc.execute("SELECT public.notification_bootstrap_tenant('c_" + companyId + "')");
        jdbc.update("DELETE FROM c_1095.notification_fanout_job");
        jdbc.update("DELETE FROM c_1095.notification_recipient_event");
        jdbc.update("DELETE FROM c_1095.notification_feed_change");
        jdbc.update("DELETE FROM c_1095.notification_recipient_audit");
        jdbc.update("DELETE FROM c_1095.notification_recipient");
        jdbc.update("DELETE FROM c_1095.notification_event");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CanonicalJsonService canonical = new CanonicalJsonService(mapper);
        DbNotificationEvent events = new DbNotificationEvent(jdbc, canonical, mapper);
        DbNotificationFanOutJob jobs = new DbNotificationFanOutJob(jdbc);
        NotificationProperties properties = new NotificationProperties();
        properties.setEnabled(true);
        NotificationControlGate gate = mock(NotificationControlGate.class);
        when(gate.isEnabled(NotificationComponent.PUBLISH)).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationControlGate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(gate);
        NotificationPublisher publisher = new NotificationPublisher(
                properties, provider, new NotificationIdempotencyService(canonical),
                events, jobs);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        return new Phase2Harness(
                companyId, userId, jdbc, mapper, canonical, events, jobs, properties,
                publisher, manager, new TransactionTemplate(manager));
    }

    private static String requireDisposableDatabaseUrl() {
        String databaseUrl = System.getenv("VLS_NOTIFICATION_PHASE2_DB_URL");
        if (databaseUrl == null
                || !databaseUrl.matches(".*/notif_[A-Za-z0-9_-]+(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "Phase 2 smoke test refuses non-disposable databases; "
                            + "database name must start with notif_");
        }
        return databaseUrl;
    }

    private static void migrate(
            String databaseUrl, String databaseUser, String databasePassword) {
        Flyway.configure()
                .dataSource(databaseUrl, databaseUser, databasePassword)
                .locations("classpath:db/migration")
                .outOfOrder(true)
                .load()
                .migrate();
    }

    private static NotificationFanOutService fanOut(
            JdbcTemplate jdbc, ObjectMapper mapper, CanonicalJsonService canonical,
            NotificationProperties properties, DbNotificationEvent events,
            DbNotificationFanOutJob jobs, DataSourceTransactionManager manager) {
        NotificationAudienceResolver audience = new NotificationAudienceResolver(jdbc);
        NotificationAggregationService aggregation = new NotificationAggregationService(
                new DbNotificationRecipient(jdbc, canonical),
                new DbNotificationRecipientEvent(jdbc),
                new DbNotificationFeedChange(jdbc));
        return new NotificationFanOutService(
                jdbc, properties, jobs, events, new DbNotificationCatalog(jdbc), audience,
                new NotificationTemplateRenderer(new DbNotificationTemplate(jdbc),
                        new NotificationPreviewRenderer()),
                aggregation, manager);
    }

    private record Phase2Harness(
            int companyId,
            int userId,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            CanonicalJsonService canonical,
            DbNotificationEvent events,
            DbNotificationFanOutJob jobs,
            NotificationProperties properties,
            NotificationPublisher publisher,
            DataSourceTransactionManager manager,
            TransactionTemplate tx) {
    }

    private static final class SimulatedAggregationCrash extends RuntimeException {
    }

    private static final class SimulatedCursorCrash extends RuntimeException {
    }
}
