package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationDeviceView;
import com.example.valueinsoftbackend.notification.service.NotificationDeviceService;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/notifications/devices")
public class NotificationDeviceController {
    private static final String CAPABILITY = "notification.device.manage.self";

    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final NotificationDeviceService devices;
    private final NotificationControlGate controls;

    public NotificationDeviceController(NotificationRequestContextResolver contexts,
                                        AuthorizationService authorization,
                                        NotificationDeviceService devices,
                                        NotificationControlGate controls) {
        this.contexts = contexts;
        this.authorization = authorization;
        this.devices = devices;
        this.controls = controls;
    }

    @PostMapping
    public NotificationDeviceView register(
            Principal principal, @Valid @RequestBody RegistrationRequest request) {
        return save(principal, request);
    }

    @PostMapping("/rotate")
    public NotificationDeviceView rotate(
            Principal principal, @Valid @RequestBody RegistrationRequest request) {
        return save(principal, request);
    }

    @DeleteMapping("/{installId}")
    public ResponseEntity<Void> logout(
            Principal principal, @PathVariable String installId) {
        var context = requireContext(principal);
        devices.logout(context.companyId(), context.userId(), installId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{installId}/shift-close")
    public ResponseEntity<Void> shiftClose(
            Principal principal, @PathVariable String installId) {
        var context = requireContext(principal);
        devices.shiftClose(context.companyId(), context.userId(), installId);
        return ResponseEntity.noContent().build();
    }

    private NotificationDeviceView save(Principal principal, RegistrationRequest request) {
        var context = requireContext(principal);
        return devices.register(
                context.companyId(),
                context.branchId(),
                context.userId(),
                new NotificationDeviceService.RegistrationCommand(
                        request.installId(),
                        request.provider(),
                        request.appBundleId(),
                        request.apnsEnvironment(),
                        request.platform(),
                        request.pushToken(),
                        request.tokenReportedAt(),
                        request.appVersion(),
                        request.osVersion(),
                        request.payloadVersionMax(),
                        request.locale(),
                        request.timezone()));
    }

    private NotificationRequestContextResolver.Context requireContext(Principal principal) {
        if (!controls.isEnabled(NotificationComponent.DEVICE_REGISTRATION)) {
            throw new com.example.valueinsoftbackend.ExceptionPack.ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_DEVICE_REGISTRATION_DISABLED",
                    "Notification device registration is temporarily unavailable");
        }
        String name = principal == null ? "" : principal.getName();
        var context = contexts.resolve(name);
        authorization.assertAuthenticatedCapability(
                name, Math.toIntExact(context.companyId()), context.branchId(), CAPABILITY);
        return context;
    }

    public record RegistrationRequest(
            @NotBlank @Size(max = 255) String installId,
            @NotBlank @Size(max = 20) String provider,
            @NotBlank @Size(max = 255) String appBundleId,
            @Size(max = 20) String apnsEnvironment,
            @NotBlank @Size(max = 20) String platform,
            @NotBlank @Size(max = 4096) String pushToken,
            OffsetDateTime tokenReportedAt,
            @Size(max = 50) String appVersion,
            @Size(max = 50) String osVersion,
            @Min(1) @Max(100) Integer payloadVersionMax,
            @Size(max = 20) String locale,
            @Size(max = 100) String timezone
    ) {
    }
}
