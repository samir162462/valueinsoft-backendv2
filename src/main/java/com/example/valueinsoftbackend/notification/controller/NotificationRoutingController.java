package com.example.valueinsoftbackend.notification.controller;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
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

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications/routing")
public class NotificationRoutingController {
    private static final String CAPABILITY = "notification.routing.manage.company";

    private final NotificationRequestContextResolver contexts;
    private final AuthorizationService authorization;
    private final NotificationRoutingService routing;

    public NotificationRoutingController(NotificationRequestContextResolver contexts,
                                         AuthorizationService authorization,
                                         NotificationRoutingService routing) {
        this.contexts = contexts;
        this.authorization = authorization;
        this.routing = routing;
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
