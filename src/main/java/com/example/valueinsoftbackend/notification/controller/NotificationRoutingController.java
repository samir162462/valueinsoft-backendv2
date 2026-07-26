package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.config.NotificationProperties;
import com.example.valueinsoftbackend.notification.control.NotificationComponent;
import com.example.valueinsoftbackend.notification.control.NotificationControlGate;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.Dashboard;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.Target;
import com.example.valueinsoftbackend.notification.service.NotificationRequestContextResolver;
import com.example.valueinsoftbackend.notification.service.NotificationRoutingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications/routing")
public class NotificationRoutingController {
    private static final String CAPABILITY = "notification.routing.manage.company";

    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final NotificationRoutingService routing;
    private final NotificationProperties properties;
    private final NotificationControlGate controls;

    public NotificationRoutingController(NotificationRequestContextResolver contexts,
                                         AuthorizationService authorization,
                                         NotificationRoutingService routing,
                                         NotificationProperties properties,
                                         NotificationControlGate controls) {
        this.contexts = contexts;
        this.authorization = authorization;
        this.routing = routing;
        this.properties = properties;
        this.controls = controls;
    }

    @GetMapping
    public Dashboard dashboard(Principal principal) {
        var context = requireContext(principal);
        return routing.dashboard(context.companyId());
    }

    @PutMapping
    public Dashboard update(Principal principal, @Valid @RequestBody UpdateRequest request) {
        var context = requireContext(principal);
        return routing.update(context.companyId(), context.userId(), request.typeKeys(),
                request.useDefault(), request.targets());
    }

    private NotificationRequestContextResolver.Context requireContext(Principal principal) {
        if (!properties.isEnabled()
                || !controls.isEnabled(NotificationComponent.FEED_READ)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "NOTIFICATION_ROUTING_DISABLED",
                    "Notification routing is unavailable while notifications are disabled");
        }
        String principalName = principal == null ? "" : principal.getName();
        var context = contexts.resolve(principalName);
        authorization.assertAuthenticatedCapability(principalName,
                Math.toIntExact(context.companyId()), context.branchId(), CAPABILITY);
        return context;
    }

    public record UpdateRequest(
            @NotEmpty @Size(max = 100) List<@Size(max = 120) String> typeKeys,
            @NotNull Boolean useDefault,
            @Size(max = 500) List<Target> targets
    ) {
    }
}
