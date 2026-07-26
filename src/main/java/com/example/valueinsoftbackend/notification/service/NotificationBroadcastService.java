package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Progress;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Request;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Row;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Target;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcast;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcastBatch;
import com.example.valueinsoftbackend.notification.repository.DbNotificationBroadcastTarget;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.NotificationAudienceResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcast creation, progress and cancellation (NC-7.3, NC-7.9, NC-7.10, NC-7.11).
 *
 * <p>The one authoritative flow (§2.3): the API writes a single {@code notification_broadcast}
 * row and returns. It writes no tenant events, no targets and no batches — those belong to
 * the planning worker. That is what lets the endpoint answer in milliseconds whether the
 * audience is fifty people or fifty thousand, and it is why {@code targetedCount} is not yet
 * known at creation time.
 */
@Service
public class NotificationBroadcastService {

    private final NotificationProperties properties;
    private final DbNotificationBroadcast broadcasts;
    private final DbNotificationBroadcastTarget targets;
    private final DbNotificationBroadcastBatch batches;
    private final DbNotificationCatalog catalog;
    private final NotificationAudienceResolver audience;
    private final CanonicalJsonService canonicalJson;
    private final NotificationRateLimiter rateLimiter;

    public NotificationBroadcastService(NotificationProperties properties,
                                        DbNotificationBroadcast broadcasts,
                                        DbNotificationBroadcastTarget targets,
                                        DbNotificationBroadcastBatch batches,
                                        DbNotificationCatalog catalog,
                                        NotificationAudienceResolver audience,
                                        CanonicalJsonService canonicalJson,
                                        NotificationRateLimiter rateLimiter) {
        this.properties = properties;
        this.broadcasts = broadcasts;
        this.targets = targets;
        this.batches = batches;
        this.catalog = catalog;
        this.audience = audience;
        this.canonicalJson = canonicalJson;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Estimate shown in the composer <em>before</em> the operator confirms.
     *
     * <p><strong>Scope limit worth stating plainly:</strong> the audience is the type's
     * {@code required_capability} within a company and optional branch — the same resolver
     * ordinary fan-out uses. Richer predicates from §3.9 (explicit role lists,
     * {@code activeSince}) are <em>not</em> implemented yet; a predicate carrying them is
     * rejected below rather than silently ignored, because a broadcast that quietly reaches
     * more people than the operator selected is the worst possible failure here.
     *
     * <p>The count is bounded at {@code AUDIENCE_ESTIMATE_CAP}. Beyond that the exact number
     * does not change any decision — both thresholds have already tripped — and an unbounded
     * count on a large tenant would put a full scan on the confirmation dialog.
     */
    public int estimateAudience(Request request) {
        assertPredicateSupported(request.audiencePredicate());
        String requiredCapability = catalog.requireActive(request.typeKey()).requiredCapability();
        return audience.countBounded(
                requireCompanyId(request), request.branchId(),
                request.typeKey(), requiredCapability, AUDIENCE_ESTIMATE_CAP);
    }

    private static final int AUDIENCE_ESTIMATE_CAP = 100_000;
    private static final java.util.Set<String> SUPPORTED_PREDICATE_KEYS =
            java.util.Set.of("branchId", "note");

    private void assertPredicateSupported(Map<String, Object> predicate) {
        if (predicate == null || predicate.isEmpty()) return;
        List<String> unsupported = predicate.keySet().stream()
                .filter(key -> !SUPPORTED_PREDICATE_KEYS.contains(key))
                .sorted()
                .toList();
        if (!unsupported.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_BROADCAST_PREDICATE_UNSUPPORTED",
                    "Unsupported audience predicate keys: " + String.join(", ", unsupported)
                            + ". Supported: " + String.join(", ", SUPPORTED_PREDICATE_KEYS));
        }
    }

    @Transactional
    public Row create(Request request, int actorUserId) {
        // Type must exist and be active; a broadcast of a deprecated type would plan a full
        // audience and then skip every target, which looks like a bug and costs a full scan.
        catalog.requireActive(request.typeKey());

        long companyScope = request.companyId() == null ? 0L : request.companyId();
        if (!rateLimiter.tryAcquire(companyScope,
                NotificationRateLimiter.BROADCAST_CREATE, String.valueOf(actorUserId))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "NOTIFICATION_BROADCAST_RATE_LIMITED",
                    "Too many broadcasts created recently; try again later");
        }

        assertConfirmationThresholds(request);

        byte[] fingerprint = fingerprintOf(request);

        return broadcasts.create(request, fingerprint)
                .orElseGet(() -> resolveExistingOrConflict(request, fingerprint));
    }

    /**
     * Semantic fingerprint of the request, over the fields that define <em>what</em> is being
     * sent to <em>whom</em>. Deliberately excludes {@code createdByUserId}, the confirmation
     * ids and {@code scheduledAt}: the same broadcast retried by a different admin, or after
     * a reschedule, is still the same broadcast.
     */
    private byte[] fingerprintOf(Request request) {
        String canonical = canonicalJson.canonicalize(new java.util.LinkedHashMap<>(Map.of(
                "typeKey", request.typeKey(),
                "scope", request.scope(),
                "companyId", String.valueOf(request.companyId()),
                "branchId", String.valueOf(request.branchId()),
                "priority", request.priority(),
                "audiencePredicate", request.audiencePredicate() == null
                        ? Map.of() : request.audiencePredicate(),
                "params", request.params() == null ? Map.of() : request.params())));
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /**
     * A repeated create with the same idempotency key is a safe retry only when the request
     * is semantically identical. A different request under the same key is a producer bug and
     * is refused loudly rather than silently sending something nobody reviewed.
     */
    private Row resolveExistingOrConflict(Request request, byte[] fingerprint) {
        byte[] existing = broadcasts.fingerprint(request.idempotencyKey())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "NOTIFICATION_BROADCAST_CONFLICT",
                        "Broadcast idempotency key is in use but unreadable"));

        if (!Arrays.equals(existing, fingerprint)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "NOTIFICATION_BROADCAST_IDEMPOTENCY_CONFLICT",
                    "This idempotency key was already used for a different broadcast");
        }
        return broadcasts.byIdempotencyKey(request.idempotencyKey())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "NOTIFICATION_BROADCAST_CONFLICT", "Broadcast not found after conflict"));
    }

    /**
     * Confirmation gates (§10). Above the first threshold a human must have typed a
     * confirmation; above the second a <em>different</em> human must have approved. The
     * second check is the one that matters — self-approval would make the control decorative.
     */
    private void assertConfirmationThresholds(Request request) {
        int estimate = estimateAudience(request);
        var broadcastConfig = properties.getBroadcast();

        if (estimate >= broadcastConfig.getConfirmThreshold()
                && request.confirmedByUserId() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_BROADCAST_CONFIRMATION_REQUIRED",
                    "This broadcast reaches approximately " + estimate
                            + " people and must be explicitly confirmed");
        }

        if (estimate >= broadcastConfig.getDualApprovalThreshold()) {
            if (request.approvedByUserId() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "NOTIFICATION_BROADCAST_APPROVAL_REQUIRED",
                        "This broadcast reaches approximately " + estimate
                                + " people and requires a second approver");
            }
            if (request.approvedByUserId().equals(request.confirmedByUserId())
                    || request.approvedByUserId() == request.createdByUserId()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "NOTIFICATION_BROADCAST_APPROVAL_SELF",
                        "The second approver must be a different administrator");
            }
        }
    }

    public Progress progress(UUID uuid) {
        return broadcasts.progress(uuid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "NOTIFICATION_BROADCAST_NOT_FOUND", "Broadcast not found"));
    }

    public List<Target> targets(UUID uuid, String status, int limit, int offset) {
        Row row = requireBroadcast(uuid);
        return targets.byStatus(row.broadcastId(), status, Math.min(limit, 500), Math.max(offset, 0));
    }

    /**
     * Cancels remaining work. Already-materialised recipients keep their feed rows — a
     * notification someone has already seen cannot be unsent, and deleting it would be a
     * worse lie than leaving it.
     */
    @Transactional
    public Progress cancel(UUID uuid) {
        Row row = requireBroadcast(uuid);
        batches.cancelPending(row.broadcastId());
        int skipped = targets.cancelPending(row.broadcastId());
        broadcasts.addBatchResult(row.broadcastId(), 0, skipped, 0);
        broadcasts.cancel(row.broadcastId());
        return progress(uuid);
    }

    @Transactional
    public void retryDeadBatch(UUID uuid, int batchNo) {
        Row row = requireBroadcast(uuid);
        batches.requeueDead(row.broadcastId(), batchNo);
    }

    private Row requireBroadcast(UUID uuid) {
        return broadcasts.byUuid(uuid)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "NOTIFICATION_BROADCAST_NOT_FOUND", "Broadcast not found"));
    }

    private long requireCompanyId(Request request) {
        if (request.companyId() == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_BROADCAST_SCOPE_UNSUPPORTED",
                    "Platform-scope broadcasts are not supported yet; specify a company");
        }
        return request.companyId();
    }

}
