package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.platform.PlatformAuthorizationService;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlPreset;
import com.example.valueinsoftbackend.notification.control.NotificationControlService;
import com.example.valueinsoftbackend.notification.control.NotificationOperatingWindowService;
import com.example.valueinsoftbackend.notification.repository.DbNotificationControl;
import com.example.valueinsoftbackend.notification.scheduler.NotificationTaskScheduler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The control plane API behind the platform-admin control screen (NC-7.18, §16.8).
 *
 * <p>Three capabilities, deliberately separate: {@code view} reads,
 * {@code toggle.component} flips an individual worker or channel, and
 * {@code toggle.module} — the master switch — is its own grant because it is a data-loss
 * action (§16.3), not merely a bigger version of the others.
 *
 * <p>The screen reads <em>two</em> things and shows both: the configured switch state, and
 * the live parked/armed status reported by the scheduler. They can disagree — an instance
 * that missed a pub/sub message keeps running until the 60-second backstop re-read — and
 * showing what the workers are <em>actually</em> doing is how that gets noticed.
 */
@RestController
@RequestMapping("/api/v1/notifications/admin/control")
@Slf4j
public class NotificationControlController {

    private static final String VIEW = "notification.control.view";
    private static final String TOGGLE_COMPONENT = "notification.control.toggle.component";
    private static final String TOGGLE_MODULE = "notification.control.toggle.module";

    private final NotificationProperties properties;
    private final PlatformAuthorizationService platformAuthorization;
    private final ObjectProvider<NotificationControlService> controlProvider;
    private final ObjectProvider<NotificationTaskScheduler> schedulerProvider;
    private final ObjectProvider<NotificationOperatingWindowService> operatingWindowProvider;
    private final DbNotificationControl repository;

    public NotificationControlController(NotificationProperties properties,
                                         PlatformAuthorizationService platformAuthorization,
                                         ObjectProvider<NotificationControlService> controlProvider,
                                         ObjectProvider<NotificationTaskScheduler> schedulerProvider,
                                         ObjectProvider<NotificationOperatingWindowService> operatingWindowProvider,
                                         DbNotificationControl repository) {
        this.properties = properties;
        this.platformAuthorization = platformAuthorization;
        this.controlProvider = controlProvider;
        this.schedulerProvider = schedulerProvider;
        this.operatingWindowProvider = operatingWindowProvider;
        this.repository = repository;
    }

    // ── Read ───────────────────────────────────────────────────────────────

    @GetMapping
    public ControlSnapshotResponse snapshot(Principal principal) {
        requireCapability(principal, VIEW);
        NotificationControlService control = requireControl();

        Set<String> armed = schedulerProvider.getIfAvailable() == null
                ? Set.of()
                : schedulerProvider.getObject().armedWorkers();

        List<ComponentView> components = new ArrayList<>();
        for (NotificationComponent component : NotificationComponent.values()) {
            components.add(new ComponentView(
                    component.key(),
                    component.scope(),
                    component.displayName(),
                    component.switchable(),
                    control.isEnabled(component),
                    control.suppressionMode(component),
                    // Only workers have a live counterpart; a channel or API surface has no
                    // scheduled task, so null means "not applicable" rather than "parked".
                    "worker".equals(component.scope()) ? armed.contains(component.key()) : null));
        }

        return new ControlSnapshotResponse(
                control.source(),
                // A DEGRADED source means the instance is running on static configuration
                // because Redis had no state — worth surfacing loudly rather than inferring.
                !"redis".equals(control.source()),
                components,
                Arrays.stream(NotificationControlPreset.values())
                        .map(preset -> new PresetView(
                                preset.name(), preset.displayName(), preset.description(),
                                preset.suppressionMode(), preset.dataLoss(),
                                preset.affectedComponents()))
                        .toList());
    }

    @GetMapping("/audit")
    public List<DbNotificationControl.ControlState> audit(Principal principal) {
        requireCapability(principal, VIEW);
        return repository.findAll();
    }

    @GetMapping("/operating-window")
    public NotificationOperatingWindowService.WindowView operatingWindow(Principal principal) {
        requireCapability(principal, VIEW);
        return requireOperatingWindow().view();
    }

    // ── Write ──────────────────────────────────────────────────────────────

    @PostMapping("/components/{componentKey}")
    public DbNotificationControl.ControlState toggle(Principal principal,
                                                     HttpServletRequest request,
                                                     @PathVariable String componentKey,
                                                     @Valid @RequestBody ToggleRequest body) {
        NotificationComponent component = parseComponent(componentKey);

        // The master switch is its own capability. Someone trusted to park the retention job
        // is not automatically trusted to stop recording notifications entirely.
        String capability = component == NotificationComponent.MODULE
                || component == NotificationComponent.PUBLISH
                ? TOGGLE_MODULE : TOGGLE_COMPONENT;
        requireCapability(principal, capability);

        if (!component.switchable()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_CONTROL_NOT_SWITCHABLE",
                    component.key() + " cannot be disabled — something has to be able to turn "
                            + "the system back on");
        }

        try {
            return requireControl().change(
                    component,
                    body.enabled(),
                    body.suppressionMode(),
                    body.reason(),
                    body.disabledUntil(),
                    0,
                    clientIp(request),
                    null);
        } catch (IllegalArgumentException ex) {
            // The service validates reason-required and suppression-mode; surface those as
            // 422 rather than letting them become a 500.
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_CONTROL_INVALID", ex.getMessage());
        }
    }

    /**
     * Applies a preset atomically-ish: each component is changed in turn, and the response
     * reports what actually landed. A partial application is possible if one change fails
     * validation, which is why the response is the resulting state rather than an "ok".
     */
    @PostMapping("/presets/{presetName}")
    public List<DbNotificationControl.ControlState> applyPreset(
            Principal principal,
            HttpServletRequest request,
            @PathVariable String presetName,
            @Valid @RequestBody PresetRequest body) {

        NotificationControlPreset preset;
        try {
            preset = NotificationControlPreset.valueOf(presetName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "NOTIFICATION_PRESET_UNKNOWN", "Unknown preset: " + presetName);
        }

        boolean touchesModule = preset.changes().containsKey(NotificationComponent.MODULE)
                || preset.changes().containsKey(NotificationComponent.PUBLISH);
        requireCapability(principal, touchesModule ? TOGGLE_MODULE : TOGGLE_COMPONENT);

        // A preset that loses notifications requires the operator to have typed the word,
        // not merely clicked. The UI collects it; this is the server-side half.
        if (preset.dataLoss() && !"STOP".equals(body.confirmation())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_PRESET_CONFIRMATION_REQUIRED",
                    "This preset stops notifications being recorded at all. Notifications raised "
                            + "while it is active cannot be recovered. Type STOP to confirm.");
        }
        if (body.reason() == null || body.reason().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_CONTROL_REASON_REQUIRED",
                    "A reason is required so the change is explicable afterwards");
        }

        NotificationControlService control = requireControl();
        List<DbNotificationControl.ControlState> applied = new ArrayList<>();
        for (var entry : preset.changes().entrySet()) {
            applied.add(control.change(
                    entry.getKey(), entry.getValue(), preset.suppressionMode(),
                    body.reason(), null, 0, clientIp(request), null));
        }

        log.warn("Notification control preset {} applied by {}: {}",
                preset.name(), principalName(principal), body.reason());
        return applied;
    }

    /**
     * Re-hydrates Redis from the durable table. The only path that reads control state from
     * PostgreSQL — startup and worker gate reads never do, which is what lets an instance
     * start while the database is suspended (§16.5).
     */
    @PostMapping("/resync")
    public List<DbNotificationControl.ControlState> resync(Principal principal) {
        requireCapability(principal, TOGGLE_COMPONENT);
        return requireControl().resyncFromDatabase();
    }

    @PutMapping("/operating-window")
    public NotificationOperatingWindowService.WindowView updateOperatingWindow(
            Principal principal,
            @Valid @RequestBody OperatingWindowRequest body) {
        requireCapability(principal, TOGGLE_COMPONENT);
        try {
            return requireOperatingWindow().update(
                    body.enabled(), body.quietStart(), body.quietEnd(), body.timezone());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "NOTIFICATION_OPERATING_WINDOW_INVALID", exception.getMessage());
        }
    }

    // ── Plumbing ───────────────────────────────────────────────────────────

    private NotificationComponent parseComponent(String key) {
        try {
            return NotificationComponent.valueOf(key.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "NOTIFICATION_COMPONENT_UNKNOWN", "Unknown component: " + key);
        }
    }

    private NotificationControlService requireControl() {
        NotificationControlService control = controlProvider.getIfAvailable();
        if (control == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_CONTROL_UNAVAILABLE",
                    "The runtime control plane is not active on this instance");
        }
        return control;
    }

    private NotificationOperatingWindowService requireOperatingWindow() {
        NotificationOperatingWindowService operatingWindow =
                operatingWindowProvider.getIfAvailable();
        if (operatingWindow == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_OPERATING_WINDOW_UNAVAILABLE",
                    "The notification resource saver is not active on this instance");
        }
        return operatingWindow;
    }

    private void requireCapability(Principal principal, String capability) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_DISABLED", "Notification module is disabled at tier 0");
        }
        platformAuthorization.requirePlatformCapability(principalName(principal), capability);
    }

    private static String principalName(Principal principal) {
        return principal == null ? "" : principal.getName();
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    // ── Wire types ─────────────────────────────────────────────────────────

    public record ComponentView(
            String key,
            String scope,
            String displayName,
            boolean switchable,
            boolean enabled,
            String suppressionMode,
            /** Null for non-workers. True = a scheduled task is armed on this instance. */
            Boolean armedOnThisInstance
    ) {
    }

    public record PresetView(
            String name,
            String displayName,
            String description,
            String suppressionMode,
            boolean dataLoss,
            List<String> affectedComponents
    ) {
    }

    public record ControlSnapshotResponse(
            String source,
            boolean degraded,
            List<ComponentView> components,
            List<PresetView> presets
    ) {
    }

    public record ToggleRequest(
            boolean enabled,
            @Size(max = 20) String suppressionMode,
            @Size(max = 500) String reason,
            OffsetDateTime disabledUntil
    ) {
    }

    public record PresetRequest(
            @Size(max = 500) String reason,
            /** Must be the literal "STOP" for a preset that loses notifications. */
            @Size(max = 20) String confirmation
    ) {
    }

    public record OperatingWindowRequest(
            boolean enabled,
            @Size(min = 5, max = 8) String quietStart,
            @Size(min = 5, max = 8) String quietEnd,
            @Size(min = 1, max = 100) String timezone
    ) {
    }
}
