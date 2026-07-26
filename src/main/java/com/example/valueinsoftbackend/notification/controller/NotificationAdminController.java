package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.DeliveryRow;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.DeviceInventorySummary;
import com.example.valueinsoftbackend.notification.model.NotificationAdmin.RetryResult;
import com.example.valueinsoftbackend.notification.service.NotificationAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

/**
 * Platform-admin delivery tooling (NC-7.12 to NC-7.15).
 *
 * <p>Note the capability split. {@code notification.admin.view} reads; retry needs
 * {@code notification.admin.retry}; and <strong>resend is not exposed here at all</strong> —
 * it creates a new notification for a real person and belongs behind its own capability and
 * its own explicit confirmation flow (§6.10). See the note on {@code /resend} below.
 */
@RestController
@RequestMapping("/api/v1/notifications/admin")
public class NotificationAdminController {

    private static final String VIEW = "notification.admin.view";
    private static final String RETRY = "notification.admin.retry";

    private final NotificationProperties properties;
    private final AuthorizationService authorization;
    private final NotificationAdminService admin;

    public NotificationAdminController(NotificationProperties properties,
                                       AuthorizationService authorization,
                                       NotificationAdminService admin) {
        this.properties = properties;
        this.authorization = authorization;
        this.admin = admin;
    }

    @GetMapping("/deliveries")
    public List<DeliveryRow> deliveries(
            Principal principal,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String errorCode,
            @RequestParam(required = false) Long broadcastId,
            @RequestParam(defaultValue = "100") int limit) {
        requirePlatformCapability(principal, VIEW);
        return admin.searchDeliveries(from, to, companyId, userId, status, errorCode,
                broadcastId, limit);
    }

    /** Full attempt history for one delivery — the whole retry sequence, in order. */
    @GetMapping("/deliveries/{outboxUuid}/attempts")
    public List<Object[]> attempts(Principal principal,
                                   @PathVariable UUID outboxUuid,
                                   @RequestParam(required = false) Instant createdAt) {
        requirePlatformCapability(principal, VIEW);
        return admin.attempts(outboxUuid, createdAt);
    }

    /**
     * Re-attempts a delivery that gave up. Creates **no** notification and does not change
     * unread state — the user sees what they were always meant to see.
     */
    @PostMapping("/deliveries/{outboxUuid}/retry")
    public RetryResult retry(Principal principal,
                             @PathVariable UUID outboxUuid,
                             @Valid @RequestBody RetryRequest request) {
        String actor = requirePlatformCapability(principal, RETRY);
        return admin.retryDelivery(outboxUuid, request.createdAt(), actor, request.reason());
    }

    /**
     * Resend is deliberately unimplemented rather than quietly aliased to retry.
     *
     * <p>It creates a new event and a new feed occurrence for a real person, so it needs its
     * own capability ({@code notification.admin.resend}, seeded in V175), an explicit typed
     * confirmation, and a recipient-scoping decision nobody has made yet. Returning a clear
     * 501 is safer than an endpoint that looks like retry and behaves differently.
     */
    @PostMapping("/resend")
    public void resend(Principal principal) {
        requirePlatformCapability(principal, VIEW);
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                "NOTIFICATION_RESEND_NOT_IMPLEMENTED",
                "Resend-as-new is not available yet. Use retry to re-attempt a failed delivery; "
                        + "resend creates a new notification and needs its own confirmation flow.");
    }

    @GetMapping("/companies/{companyId}/devices")
    public DeviceInventorySummary devices(Principal principal,
                                          @PathVariable long companyId,
                                          @RequestParam(defaultValue = "200") int limit) {
        requirePlatformCapability(principal, VIEW);
        return admin.deviceInventory(companyId, limit);
    }

    /**
     * Platform-scope authorisation: these endpoints span tenants, so the capability is checked
     * without a company or branch scope.
     *
     * <p>Returns the principal <em>name</em> rather than a numeric id. A platform admin may
     * have no company context at all, so the tenant-scoped context resolver cannot be used
     * here — and the name is what an auditor reading the trail actually wants to see.
     */
    private String requirePlatformCapability(Principal principal, String capability) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_DISABLED", "Notification module is disabled");
        }
        String principalName = principal == null ? "" : principal.getName();
        authorization.assertAuthenticatedCapability(principalName, null, null, capability);
        return principalName;
    }

    public record RetryRequest(
            /** Partition hint from the search result; without it the lookup scans 90 days. */
            Instant createdAt,
            @NotBlank @Size(max = 300) String reason
    ) {
    }
}
