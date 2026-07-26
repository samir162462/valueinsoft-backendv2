package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.EffectiveType;
import com.example.valueinsoftbackend.notification.model.NotificationPreference.GlobalPreference;
import com.example.valueinsoftbackend.notification.service.NotificationPreferenceService;
import com.example.valueinsoftbackend.notification.service.NotificationPreferenceService.TypeUpdate;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
public class NotificationPreferenceController {
    private static final String CAPABILITY = "notification.preference.manage.self";

    private final NotificationProperties properties;
    private final ObjectProvider<NotificationControlGate> gateProvider;
    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final NotificationPreferenceService preferences;

    public NotificationPreferenceController(NotificationProperties properties,
                                            ObjectProvider<NotificationControlGate> gateProvider,
                                            NotificationRequestContextResolver contexts,
                                            AuthorizationService authorization,
                                            NotificationPreferenceService preferences) {
        this.properties = properties;
        this.gateProvider = gateProvider;
        this.contexts = contexts;
        this.authorization = authorization;
        this.preferences = preferences;
    }

    @GetMapping
    public PreferencesResponse list(Principal principal) {
        var context = requireContext(principal);
        return new PreferencesResponse(
                preferences.effectiveTypes(context.companyId(), context.userId()),
                preferences.globalPreference(context.companyId(), context.userId()),
                preferences.quietHoursActive(context.companyId(), context.userId()));
    }

    /**
     * Bulk upsert. Immutable types produce 422 listing every offending key — see
     * {@code NotificationPreferenceService.updateTypes}.
     */
    @PutMapping
    public List<EffectiveType> update(Principal principal,
                                      @Valid @RequestBody TypeUpdateRequest request) {
        var context = requireContext(principal);
        return preferences.updateTypes(
                context.companyId(),
                context.userId(),
                request.preferences().stream()
                        .map(item -> new TypeUpdate(
                                item.typeKey(), item.channelInApp(),
                                item.channelPush(), item.mutedUntil()))
                        .toList());
    }

    @GetMapping("/global")
    public GlobalPreference global(Principal principal) {
        var context = requireContext(principal);
        return preferences.globalPreference(context.companyId(), context.userId());
    }

    @PutMapping("/global")
    public GlobalPreference updateGlobal(Principal principal,
                                         @Valid @RequestBody GlobalPreferenceRequest request) {
        var context = requireContext(principal);
        return preferences.updateGlobal(context.companyId(), context.userId(),
                new GlobalPreference(
                        NotificationPreferenceService.parseLocalTime(request.quietHoursStart()),
                        NotificationPreferenceService.parseLocalTime(request.quietHoursEnd()),
                        request.quietHoursTz() == null ? "UTC" : request.quietHoursTz(),
                        request.dndUntil(),
                        request.minPriority() == null ? "low" : request.minPriority(),
                        request.digestMode() == null ? "off" : request.digestMode()));
    }

    private NotificationRequestContextResolver.Context requireContext(Principal principal) {
        NotificationControlGate gate = gateProvider.getIfAvailable();
        // Preferences ride the FEED_READ switch: if a tenant's feed is off, editing which
        // notifications they would have received is meaningless.
        if (!properties.isEnabled()
                || (gate != null && !gate.isEnabled(NotificationComponent.FEED_READ))) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_FEED_DISABLED", "Notification preferences are temporarily disabled");
        }
        String principalName = principal == null ? "" : principal.getName();
        var context = contexts.resolve(principalName);
        authorization.assertAuthenticatedCapability(principalName,
                Math.toIntExact(context.companyId()), context.branchId(), CAPABILITY);
        return context;
    }

    public record PreferencesResponse(
            List<EffectiveType> types,
            GlobalPreference global,
            boolean quietHoursActiveNow
    ) {
    }

    public record TypeUpdateRequest(@NotEmpty @Valid List<TypeUpdateItem> preferences) {
    }

    public record TypeUpdateItem(
            @NotBlank @Size(max = 120) String typeKey,
            Boolean channelInApp,
            Boolean channelPush,
            Instant mutedUntil
    ) {
    }

    public record GlobalPreferenceRequest(
            @Size(max = 8) String quietHoursStart,
            @Size(max = 8) String quietHoursEnd,
            @Size(max = 100) String quietHoursTz,
            Instant dndUntil,
            @Size(max = 20) String minPriority,
            @Size(max = 20) String digestMode
    ) {
    }
}
