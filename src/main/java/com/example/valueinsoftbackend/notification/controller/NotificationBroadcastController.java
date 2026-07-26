package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Progress;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Request;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Row;
import com.example.valueinsoftbackend.notification.model.NotificationBroadcast.Target;
import com.example.valueinsoftbackend.notification.service.NotificationBroadcastService;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Broadcast endpoints (NC-7.3, NC-7.9, NC-7.10, NC-7.11).
 *
 * <p>{@code POST /broadcast} returns as soon as the parent row is written — it does not wait
 * for planning, and therefore cannot report {@code targetedCount}. That is the whole point of
 * §2.3: an operator addressing fifty thousand people gets the same sub-second response as one
 * addressing fifty. The composer polls {@code GET /broadcast/{uuid}} for progress.
 */
@RestController
@RequestMapping("/api/v1/notifications/broadcast")
public class NotificationBroadcastController {

    private static final String BRANCH_CAPABILITY = "notification.broadcast.send.branch";
    private static final String COMPANY_CAPABILITY = "notification.broadcast.send.company";

    private final NotificationProperties properties;
    private final ObjectProvider<NotificationControlGate> gateProvider;
    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final NotificationBroadcastService broadcasts;

    public NotificationBroadcastController(NotificationProperties properties,
                                           ObjectProvider<NotificationControlGate> gateProvider,
                                           NotificationRequestContextResolver contexts,
                                           AuthorizationService authorization,
                                           NotificationBroadcastService broadcasts) {
        this.properties = properties;
        this.gateProvider = gateProvider;
        this.contexts = contexts;
        this.authorization = authorization;
        this.broadcasts = broadcasts;
    }

    /**
     * Audience estimate for the confirmation dialog. Separate from creation so the composer
     * can show the number <em>before</em> the operator commits — a confirm dialog that says
     * "are you sure?" without saying "how many" is not a control.
     */
    @PostMapping("/estimate")
    public EstimateResponse estimate(Principal principal, @Valid @RequestBody CreateRequest body) {
        var context = requireContext(principal, body);
        int estimate = broadcasts.estimateAudience(toRequest(body, context.companyId(), context.userId()));
        var config = properties.getBroadcast();
        return new EstimateResponse(
                estimate,
                estimate >= config.getConfirmThreshold(),
                estimate >= config.getDualApprovalThreshold());
    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(Principal principal,
                                                 @Valid @RequestBody CreateRequest body) {
        var context = requireContext(principal, body);
        Row row = broadcasts.create(
                toRequest(body, context.companyId(), context.userId()), context.userId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new CreateResponse(row.broadcastUuid(), row.status()));
    }

    @GetMapping("/{broadcastUuid}")
    public Progress progress(Principal principal, @PathVariable UUID broadcastUuid) {
        requireViewContext(principal);
        return broadcasts.progress(broadcastUuid);
    }

    /**
     * The target list, filterable by status. This is where "12,000 skipped" becomes
     * "12,000 skipped because they have no active device" — the counters alone are alarming
     * without it.
     */
    @GetMapping("/{broadcastUuid}/targets")
    public List<Target> targets(Principal principal,
                                @PathVariable UUID broadcastUuid,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "100") int limit,
                                @RequestParam(defaultValue = "0") int offset) {
        requireViewContext(principal);
        return broadcasts.targets(broadcastUuid, status, limit, offset);
    }

    @PostMapping("/{broadcastUuid}/cancel")
    public Progress cancel(Principal principal, @PathVariable UUID broadcastUuid) {
        requireViewContext(principal);
        return broadcasts.cancel(broadcastUuid);
    }

    /** Requeues a batch that exhausted its attempts. Safe: a dead batch materialised nothing. */
    @PostMapping("/{broadcastUuid}/batches/{batchNo}/retry")
    public Progress retryBatch(Principal principal,
                               @PathVariable UUID broadcastUuid,
                               @PathVariable int batchNo) {
        requireViewContext(principal);
        broadcasts.retryDeadBatch(broadcastUuid, batchNo);
        return broadcasts.progress(broadcastUuid);
    }

    // ── Authorisation ──────────────────────────────────────────────────────

    /**
     * A branch-scoped broadcast needs only the branch capability; anything wider needs the
     * company one. Checking the narrower capability for a company-wide send would let a
     * branch manager address the entire tenant.
     */
    private NotificationRequestContextResolver.Context requireContext(Principal principal,
                                                                     CreateRequest body) {
        requireEnabled();
        String principalName = principal == null ? "" : principal.getName();
        var context = contexts.resolve(principalName);
        String capability = "branch".equals(body.scope()) ? BRANCH_CAPABILITY : COMPANY_CAPABILITY;
        authorization.assertAuthenticatedCapability(principalName,
                Math.toIntExact(context.companyId()), context.branchId(), capability);
        return context;
    }

    private NotificationRequestContextResolver.Context requireViewContext(Principal principal) {
        requireEnabled();
        String principalName = principal == null ? "" : principal.getName();
        var context = contexts.resolve(principalName);
        authorization.assertAuthenticatedCapability(principalName,
                Math.toIntExact(context.companyId()), context.branchId(), BRANCH_CAPABILITY);
        return context;
    }

    private void requireEnabled() {
        NotificationControlGate gate = gateProvider.getIfAvailable();
        if (!properties.isEnabled()
                || (gate != null && !gate.isEnabled(NotificationComponent.BROADCAST_CREATE))) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_BROADCAST_DISABLED",
                    "Broadcast creation is temporarily disabled");
        }
    }

    private Request toRequest(CreateRequest body, long companyId, int actorUserId) {
        return new Request(
                body.scope(),
                companyId,
                body.branchId(),
                body.typeKey(),
                body.audiencePredicate() == null ? Map.of() : body.audiencePredicate(),
                body.params() == null ? Map.of() : body.params(),
                body.priority() == null ? "normal" : body.priority(),
                body.idempotencyKey(),
                body.scheduledAt(),
                actorUserId,
                body.confirmedByUserId(),
                body.approvedByUserId());
    }

    // ── Wire types ─────────────────────────────────────────────────────────

    public record CreateRequest(
            @NotBlank @Size(max = 20) String scope,
            Integer branchId,
            @NotBlank @Size(max = 120) String typeKey,
            Map<String, Object> audiencePredicate,
            Map<String, Object> params,
            @Size(max = 20) String priority,
            @NotBlank @Size(max = 255) String idempotencyKey,
            Instant scheduledAt,
            /** Set by the UI once the operator has typed the confirmation. */
            Integer confirmedByUserId,
            /** A second administrator, required above the dual-approval threshold. */
            Integer approvedByUserId
    ) {
    }

    public record CreateResponse(UUID broadcastUuid, String status) {
    }

    public record EstimateResponse(
            int estimatedRecipients,
            boolean confirmationRequired,
            boolean secondApproverRequired
    ) {
    }
}
