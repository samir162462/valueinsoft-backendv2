package com.example.valueinsoftbackend.companyinsights.security;

import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.ai.service.AiSecurityContextResolver;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Authorization + tenant-context resolution for Company Smart Insights. All checks are
 * company-scoped (branchId = null). The company is always taken from the resolved security
 * context, never from client input.
 */
@Service
public class CompanyInsightSecurityService {

    public static final String VIEW = "company.insights.view";
    public static final String AI = "company.insights.ai";
    public static final String CONFIGURE = "company.insights.configure";
    public static final String ADMIN = "company.insights.admin";

    private final AiSecurityContextResolver securityContextResolver;
    private final AuthorizationService authorizationService;

    public CompanyInsightSecurityService(AiSecurityContextResolver securityContextResolver,
                                         AuthorizationService authorizationService) {
        this.securityContextResolver = securityContextResolver;
        this.authorizationService = authorizationService;
    }

    public AiSecurityContext authorizeView(Principal principal) {
        return require(principal, VIEW);
    }

    public AiSecurityContext authorizeConfigure(Principal principal) {
        return require(principal, CONFIGURE);
    }

    public AiSecurityContext authorizeAdmin(Principal principal) {
        return require(principal, ADMIN);
    }

    private AiSecurityContext require(Principal principal, String capability) {
        AiSecurityContext context = securityContextResolver.resolve(principal);
        authorizationService.assertAuthenticatedCapability(
                context.username(),
                Math.toIntExact(context.companyId()),
                null,
                capability
        );
        return context;
    }
}
