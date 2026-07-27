package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSessionAccessResponse;
import com.example.valueinsoftbackend.Service.platform.PlatformAuthorizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Authenticated platform access bootstrap.
 *
 * <p>This endpoint intentionally does not resolve a tenant or branch. Platform-only operators
 * need their global grants before the frontend can authorize a WebAdmin route.
 */
@RestController
@RequestMapping("/api/platform-admin/session")
public class PlatformAdminSessionController {

    private final PlatformAuthorizationService platformAuthorizationService;

    public PlatformAdminSessionController(
            PlatformAuthorizationService platformAuthorizationService) {
        this.platformAuthorizationService = platformAuthorizationService;
    }

    @GetMapping("/access")
    public PlatformSessionAccessResponse access(Principal principal) {
        return platformAuthorizationService.getPlatformAccess(
                principal == null ? "" : principal.getName());
    }
}
