package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.AudienceMember;
import com.example.valueinsoftbackend.notification.model.NotificationCatalogEntry;
import com.example.valueinsoftbackend.notification.model.NotificationEvent;
import com.example.valueinsoftbackend.notification.model.NotificationFanOutJob;
import com.example.valueinsoftbackend.notification.model.RenderedNotification;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.DbNotificationEvent;
import com.example.valueinsoftbackend.notification.repository.DbNotificationFanOutJob;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class NotificationFanOutService {
    public static final String SINGLE = "SINGLE_BATCH_FANOUT";
    public static final String CURSOR = "CURSOR_BATCH_FANOUT";

    private final JdbcTemplate jdbc;
    private final NotificationProperties properties;
    private final DbNotificationFanOutJob jobs;
    private final DbNotificationEvent events;
    private final DbNotificationCatalog catalog;
    private final NotificationAudienceResolver audience;
    private final NotificationTemplateRenderer renderer;
    private final NotificationAggregationService aggregation;
    private final TransactionTemplate transactions;
    private final Counter conflictRetryCounter;
    private NotificationPushMaterializationService pushMaterialization;

    @Autowired
    public NotificationFanOutService(JdbcTemplate jdbc,
                                     NotificationProperties properties,
                                     DbNotificationFanOutJob jobs,
                                     DbNotificationEvent events,
                                     DbNotificationCatalog catalog,
                                     NotificationAudienceResolver audience,
                                     NotificationTemplateRenderer renderer,
                                     NotificationAggregationService aggregation,
                                     PlatformTransactionManager transactionManager,
                                     MeterRegistry meterRegistry) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.jobs = jobs;
        this.events = events;
        this.catalog = catalog;
        this.audience = audience;
        this.renderer = renderer;
        this.aggregation = aggregation;
        this.transactions = new TransactionTemplate(transactionManager);
        this.conflictRetryCounter = Counter.builder("notification.fanout.conflict_retry")
                .description("Whole fan-out batch retries caused by an aggregation unique race")
                .register(meterRegistry);
    }

    /**
     * Convenience constructor for focused repository tests that do not start Spring.
     */
    public NotificationFanOutService(JdbcTemplate jdbc,
                                     NotificationProperties properties,
                                     DbNotificationFanOutJob jobs,
                                     DbNotificationEvent events,
                                     DbNotificationCatalog catalog,
                                     NotificationAudienceResolver audience,
                                     NotificationTemplateRenderer renderer,
                                     NotificationAggregationService aggregation,
                                     PlatformTransactionManager transactionManager) {
        this(jdbc, properties, jobs, events, catalog, audience, renderer, aggregation,
                transactionManager, new SimpleMeterRegistry());
    }

    @Autowired(required = false)
    public void configurePushMaterialization(
            NotificationPushMaterializationService pushMaterialization) {
        this.pushMaterialization = pushMaterialization;
    }

    public List<Long> tenantIds() {
        return jdbc.query("""
                SELECT tenant_id FROM public.tenants
                WHERE to_regnamespace('c_' || tenant_id::text) IS NOT NULL
                ORDER BY tenant_id
                """, (rs, rowNum) -> rs.getLong(1));
    }

    /**
     * TX-2a claims and decides mode. The TransactionTemplate returns only after commit, so no
     * recipient materialisation can begin while mode is NULL.
     */
    public Optional<NotificationFanOutJob> claimAndDecide(long companyId, String workerId) {
        try {
            return transactions.execute(status -> {
                Optional<NotificationFanOutJob> claimed = jobs.claimOne(
                        companyId, workerId, properties.getFanOut().getLeaseSeconds());
                if (claimed.isEmpty()) {
                    return Optional.empty();
                }
                NotificationFanOutJob job = claimed.get();
                if (job.mode() != null) {
                    return Optional.of(job);
                }
                NotificationEvent event = events.require(companyId, job.eventId());
                NotificationCatalogEntry type = catalog.requireActive(event.typeKey());
                int threshold = properties.getFanOut().getSingleBatchThreshold();
                jdbc.execute("SET LOCAL statement_timeout = "
                        + properties.getFanOut().getProbeTimeoutMs());
                int bounded = audience.countBounded(companyId, event.branchId(),
                        event.typeKey(), type.requiredCapability(), threshold + 1);
                String mode = bounded <= threshold ? SINGLE : CURSOR;
                jobs.decideMode(job, mode, bounded);
                return Optional.of(new NotificationFanOutJob(
                        companyId, job.jobId(), job.eventId(), mode, bounded,
                        job.fanoutCursor(), job.attemptCount()));
            });
        } catch (DataAccessException ex) {
            if (!isStatementTimeout(ex)) {
                throw ex;
            }
            // PostgreSQL aborts the transaction after statement_timeout, so the safe-direction
            // cursor decision must happen in a fresh TX after the failed claim rolls back.
            return transactions.execute(status -> {
                Optional<NotificationFanOutJob> claimed = jobs.claimOne(
                        companyId, workerId, properties.getFanOut().getLeaseSeconds());
                if (claimed.isEmpty()) {
                    return Optional.empty();
                }
                NotificationFanOutJob job = claimed.get();
                if (job.mode() != null) {
                    return Optional.of(job);
                }
                int bounded = properties.getFanOut().getSingleBatchThreshold() + 1;
                jobs.decideMode(job, CURSOR, bounded);
                return Optional.of(new NotificationFanOutJob(
                        companyId, job.jobId(), job.eventId(), CURSOR, bounded,
                        job.fanoutCursor(), job.attemptCount()));
            });
        }
    }

    public void materialize(NotificationFanOutJob job) {
        boolean complete = false;
        NotificationFanOutJob current = job;
        while (!complete) {
            BatchProgress result = materializeNextBatch(current);
            complete = result.complete();
            if (!complete) {
                current = new NotificationFanOutJob(
                        current.companyId(), current.jobId(), current.eventId(),
                        current.mode(), current.boundedAudience(), result.cursor(),
                        current.attemptCount());
            }
        }
    }

    /**
     * Materialises one transactionally committed batch. This is the resumable unit used by
     * the worker loop and by recovery tests that simulate a process stopping between batches.
     */
    public BatchProgress materializeNextBatch(NotificationFanOutJob job) {
        try {
            BatchResult result = materializeBatchWithRetry(job);
            if (!result.complete()) {
                jobs.heartbeat(job, properties.getFanOut().getLeaseSeconds());
            }
            return new BatchProgress(result.complete(), result.cursor());
        } catch (RuntimeException ex) {
            transactions.executeWithoutResult(status -> jobs.fail(job, ex.toString()));
            throw ex;
        }
    }

    private BatchResult materializeBatchWithRetry(NotificationFanOutJob job) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                BatchResult result = transactions.execute(status -> materializeBatch(job));
                if (result == null) {
                    throw new IllegalStateException("Materialisation transaction returned no result");
                }
                return result;
            } catch (DataAccessException ex) {
                if (!isUniqueViolation(ex) || attempt == 3) {
                    throw ex;
                }
                conflictRetryCounter.increment();
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(10, 41));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during aggregation conflict retry",
                            interrupted);
                }
            } catch (NotificationAggregationService.AlreadyAppliedException ex) {
                // A simultaneous worker won the lineage race. Retrying the whole transaction
                // observes its committed lineage and takes the fast no-op path.
                if (attempt == 3) {
                    throw ex;
                }
            }
        }
        throw new IllegalStateException("Unreachable aggregation retry state");
    }

    /**
     * Materialises one event for one user, reusing exactly the ordinary fan-out pipeline —
     * render, aggregate, push (NC-7.6).
     *
     * <p>The broadcast materialisation worker calls this per snapshotted target rather than
     * reimplementing the loop above. Duplicating it would mean two aggregation call sites
     * that drift, and the aggregation ordering in {@link NotificationAggregationService} is
     * precisely the thing that must not be reimplemented casually (ADR-14).
     *
     * @return the recipient uuid the target should record, and how many push outbox rows were
     *         created. Zero outbox rows is normal: the user may have no active device, or
     *         their preferences may have suppressed the push while the feed row still exists.
     */
    public SingleUserResult materializeSingleUser(long companyId, int userId, String locale,
                                                  NotificationEvent event,
                                                  NotificationCatalogEntry type) {
        RenderedNotification rendered = renderer.render(event, type, locale);
        NotificationAggregationService.Outcome outcome =
                aggregation.apply(companyId, userId, event, type, rendered);
        if (outcome.alreadyApplied() || pushMaterialization == null) {
            return new SingleUserResult(outcome.recipientUuid(), 0);
        }
        int created = pushMaterialization.materialize(
                companyId, userId, outcome.recipientId(), outcome.recipientUuid(),
                outcome.aggregateCount(), event, type, rendered);
        return new SingleUserResult(outcome.recipientUuid(), created);
    }

    public record SingleUserResult(java.util.UUID recipientUuid, int outboxCreated) {
    }

    private BatchResult materializeBatch(NotificationFanOutJob job) {
        NotificationEvent event = events.require(job.companyId(), job.eventId());
        NotificationCatalogEntry type = catalog.requireActive(event.typeKey());
        int cursor = job.fanoutCursor() == null ? 0 : job.fanoutCursor();
        int limit = SINGLE.equals(job.mode())
                ? properties.getFanOut().getSingleBatchThreshold() + 1
                : properties.getFanOut().getBatchSize();
        List<AudienceMember> members = audience.fetchBatch(
                job.companyId(), event.branchId(), event.typeKey(),
                type.requiredCapability(), cursor, limit);
        int created = 0;
        int lastCursor = cursor;
        for (AudienceMember member : members) {
            RenderedNotification rendered = renderer.render(event, type, member.locale());
            NotificationAggregationService.Outcome outcome =
                    aggregation.apply(job.companyId(), member.userId(), event, type, rendered);
            if (outcome.created()) {
                created++;
            }
            if (!outcome.alreadyApplied() && pushMaterialization != null) {
                pushMaterialization.materialize(
                        job.companyId(),
                        member.userId(),
                        outcome.recipientId(),
                        outcome.recipientUuid(),
                        outcome.aggregateCount(),
                        event,
                        type,
                        rendered);
            }
            lastCursor = member.userId();
        }
        boolean complete = SINGLE.equals(job.mode()) || members.size() < limit;
        jobs.advance(job, lastCursor, created, complete);
        return new BatchResult(complete, lastCursor);
    }

    private static boolean isUniqueViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql
                    && "23505".equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isStatementTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof java.sql.SQLException sql
                    && "57014".equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record BatchProgress(boolean complete, int cursor) {}

    private record BatchResult(boolean complete, int cursor) {}
}
