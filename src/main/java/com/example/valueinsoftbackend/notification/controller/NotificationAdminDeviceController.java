package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.service.NotificationDeviceService;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/admin/devices")
public class NotificationAdminDeviceController {
    private static final String CAPABILITY = "notification.device.manage.any";

    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final NotificationDeviceService devices;
    private final NotificationProperties properties;
    private final NotificationControlGate controls;

    public NotificationAdminDeviceController(
            NotificationRequestContextResolver contexts,
            AuthorizationService authorization,
            NotificationDeviceService devices,
            NotificationProperties properties,
            NotificationControlGate controls) {
        this.contexts = contexts;
        this.authorization = authorization;
        this.devices = devices;
        this.properties = properties;
        this.controls = controls;
    }

    @PostMapping("/{deviceUuid}/revoke")
    public ResponseEntity<Void> revoke(
            Principal principal, @PathVariable UUID deviceUuid) {
        if (!properties.isEnabled()
                || !controls.isEnabled(NotificationComponent.MODULE)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_DISABLED", "Notification module is disabled");
        }
        String principalName = principal == null ? "" : principal.getName();
        var context = contexts.resolve(principalName);
        authorization.assertAuthenticatedCapability(
                principalName,
                Math.toIntExact(context.companyId()),
                context.branchId(),
                CAPABILITY);
        devices.adminRevoke(context.companyId(), context.userId(), deviceUuid);
        return ResponseEntity.noContent().build();
    }
}
