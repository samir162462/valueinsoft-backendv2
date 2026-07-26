package com.example.valueinsoftbackend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Root configuration for the Notification Center.
 *
 * <p>{@code valueinsoft.notification.enabled} is tier 0 of the control plane
 * (NOTIFICATION_CENTER_PLAN.md §16.1): when false, no notification bean is created at all,
 * so there is no scheduler, no worker and no database traffic from this module. It defaults
 * to {@code false} so that merging notification code changes nothing until it is switched on
 * deliberately, matching the {@code valueinsoft.pos.offline.worker.enabled} pattern.
 *
 * <p>Tiers 1–3 (runtime switches driven from the platform admin screen) are held in Redis and
 * are <em>not</em> configured here; the values in {@link NotificationControlProperties} are only
 * the static fallback used when Redis has no state.
 */
@Component
@ConfigurationProperties(prefix = "valueinsoft.notification")
@Getter
@Setter
public class NotificationProperties {

    /** Tier 0 master switch. Requires a restart. See §16.1. */
    private boolean enabled = false;

    private Scheduler scheduler = new Scheduler();
    private FanOut fanOut = new FanOut();
    private Dispatch dispatch = new Dispatch();
    private Reaper reaper = new Reaper();
    private Sse sse = new Sse();
    private Payload payload = new Payload();
    private Broadcast broadcast = new Broadcast();
    private Retention retention = new Retention();

    /**
     * The notification module's private scheduler. This codebase intentionally has no
     * global {@code @EnableScheduling}, which is why every other {@code @Scheduled} method
     * in the application is inert and PostgreSQL is free to suspend while idle. The
     * notification workers therefore run on their own {@code ThreadPoolTaskScheduler},
     * registered task by task, so that switching them on cannot wake any other module's
     * dormant jobs. See §16.4.
     */
    @Getter
    @Setter
    public static class Scheduler {
        private int poolSize = 2;
        private String threadNamePrefix = "notif-sched-";
        private int shutdownAwaitSeconds = 30;
        /**
         * Safety net that re-checks parked tasks on a timer in case a control-change event
         * was missed. Off by default: re-arming is event-driven, so with everything parked
         * the module holds no timers at all.
         */
        private boolean supervisorEnabled = false;
        private long supervisorIntervalMs = 30_000L;
    }

    /** Fan-out worker. See §2.2 and §6.2. */
    @Getter
    @Setter
    public static class FanOut {
        /** Audience size that still fits a single worker transaction. Above this: CURSOR_BATCH_FANOUT. */
        private int singleBatchThreshold = 500;
        /** Users materialised per cursor transaction. */
        private int batchSize = 500;
        /** statement_timeout for the bounded audience probe. A timeout selects cursor mode. */
        private int probeTimeoutMs = 250;
        /** Jobs claimed per poll. */
        private int claimBatchSize = 20;
        private long pollDelayMs = 1_000L;
        private int leaseSeconds = 300;
        private int maxAttempts = 5;
        /** Heartbeat interval for long jobs extending their claim. */
        private int leaseHeartbeatSeconds = 60;
    }

    /** Dispatch worker and provider call budget. See §6.1 and §6.3. */
    @Getter
    @Setter
    public static class Dispatch {
        private int claimBatchSize = 100;
        private long pollDelayMs = 1_000L;
        private int leaseSeconds = 120;
        private int maxAttempts = 6;
        /** Ceiling on provider calls per second per instance. Bounds a post-outage burst (§11.3). */
        private int maxPerSecond = 500;
        /** Throttle applied for the first minutes after a control-plane resume (§16.7). */
        private int resumeMaxPerSecond = 200;
        private int providerTimeoutMs = 10_000;
        /** Backoff schedule in seconds: 1m, 2m, 10m, 1h, 6h, then dead. Jitter is ±20%. */
        private long[] backoffSeconds = {60L, 120L, 600L, 3_600L, 21_600L};
        private double backoffJitterRatio = 0.20d;
    }

    /** Stuck-claim recovery. See §6.2. */
    @Getter
    @Setter
    public static class Reaper {
        private long pollDelayMs = 30_000L;
        private int batchSize = 500;
    }

    /** Server-sent events. See §7.4. */
    @Getter
    @Setter
    public static class Sse {
        private int ticketTtlSeconds = 30;
        private int pingIntervalSeconds = 25;
        private int maxConnectionsPerUser = 3;
        private int maxConnectionsPerInstance = 5_000;
        /** Maximum changes replayed from Last-Event-ID before a reset event is sent instead. */
        private int replayLimit = 200;
        private long connectionTimeoutMs = 3_600_000L;
    }

    /**
     * Push payload limits. The database CHECK constraint is coarse protection only;
     * the authoritative validation happens after provider-specific serialization (§6.7).
     */
    @Getter
    @Setter
    public static class Payload {
        /** FCM and APNs both cap at 4096 bytes. 3800 leaves headroom for provider-added headers. */
        private int maxBytes = 3_800;
        private int currentVersion = 1;
        private int previewFallbackChars = 60;
    }

    /** Broadcast planning and confirmation thresholds. See §10 and §3.9. */
    @Getter
    @Setter
    public static class Broadcast {
        private int targetInsertChunkSize = 5_000;
        private int batchSize = 500;
        private long planningPollDelayMs = 5_000L;
        private long materializationPollDelayMs = 2_000L;
        private int planningLeaseSeconds = 900;
        private int batchLeaseSeconds = 600;
        private int batchMaxAttempts = 5;
        /** Above this a typed confirmation is required. */
        private int confirmThreshold = 1_000;
        /** Above this a second admin must approve. */
        private int dualApprovalThreshold = 50_000;
    }

    /** Retention windows. See §11.5. */
    @Getter
    @Setter
    public static class Retention {
        private int defaultRecipientDays = 180;
        private int sensitiveRecipientDays = 1_095;
        private int archivedRecipientDays = 30;
        private int feedChangeDays = 7;
        private int deliveryDedupDays = 30;
        private int deliveryAttemptDays = 30;
        private int outboxPartitionDays = 90;
        private int revokedDeviceDays = 60;
        private int purgeChunkSize = 1_000;
        private int partitionsAheadMonths = 3;
    }
}
