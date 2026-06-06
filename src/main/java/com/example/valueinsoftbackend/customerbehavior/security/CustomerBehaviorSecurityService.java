package com.example.valueinsoftbackend.customerbehavior.security;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Service.AuthorizationService;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.example.valueinsoftbackend.ai.service.AiSecurityContextResolver;
import com.example.valueinsoftbackend.customerbehavior.dto.CustomerBehaviorFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class CustomerBehaviorSecurityService {

    public static final String VIEW = "customer.behavior.view";
    public static final String AI = "customer.behavior.ai";
    public static final String EXPORT = "customer.behavior.export";
    public static final String CONFIGURE = "customer.behavior.configure";
    private static final String CLIENT_READ = "clients.account.read";
    private static final String POS_READ = "pos.sale.read";

    private final AiSecurityContextResolver securityContextResolver;
    private final AuthorizationService authorizationService;

    public CustomerBehaviorSecurityService(AiSecurityContextResolver securityContextResolver,
                                           AuthorizationService authorizationService) {
        this.securityContextResolver = securityContextResolver;
        this.authorizationService = authorizationService;
    }

    public CustomerBehaviorRequestContext authorizeView(Principal principal, CustomerBehaviorFilter filter) {
        return authorizeView(securityContextResolver.resolve(principal), filter);
    }

    public CustomerBehaviorRequestContext authorizeView(AiSecurityContext context, CustomerBehaviorFilter filter) {
        List<Integer> branchIds = normalizeBranchIds(context, filter == null ? null : filter.branchIds());
        for (Integer branchId : branchIds) {
            validateBranch(context, branchId);
            require(context, branchId, VIEW);
            require(context, branchId, CLIENT_READ);
            require(context, branchId, POS_READ);
        }
        return new CustomerBehaviorRequestContext(context, branchIds);
    }

    public CustomerBehaviorRequestContext authorizeAi(AiSecurityContext context, CustomerBehaviorFilter filter) {
        CustomerBehaviorRequestContext requestContext = authorizeView(context, filter);
        for (Integer branchId : requestContext.branchIds()) {
            require(context, branchId, AI);
        }
        return requestContext;
    }

    public CustomerBehaviorRequestContext authorizeAi(Principal principal, CustomerBehaviorFilter filter) {
        return authorizeAi(securityContextResolver.resolve(principal), filter);
    }

    public AiSecurityContext authorizeConfigRead(Principal principal) {
        AiSecurityContext context = securityContextResolver.resolve(principal);
        Integer branchId = context.defaultBranchId() == null
                ? context.allowedBranchIds().stream().findFirst().map(Math::toIntExact).orElse(null)
                : Math.toIntExact(context.defaultBranchId());
        require(context, branchId, VIEW);
        return context;
    }

    public AiSecurityContext authorizeConfigWrite(Principal principal) {
        AiSecurityContext context = securityContextResolver.resolve(principal);
        require(context, null, CONFIGURE);
        return context;
    }

    private List<Integer> normalizeBranchIds(AiSecurityContext context, List<Integer> requestedBranchIds) {
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        if (requestedBranchIds == null || requestedBranchIds.isEmpty()) {
            for (Long branchId : context.allowedBranchIds()) {
                normalized.add(Math.toIntExact(branchId));
            }
        } else {
            for (Integer branchId : requestedBranchIds) {
                if (branchId == null || branchId <= 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_BEHAVIOR_INVALID_BRANCH", "branchIds must contain positive branch IDs");
                }
                normalized.add(branchId);
            }
        }

        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOMER_BEHAVIOR_BRANCH_REQUIRED", "At least one authorized branch is required");
        }
        return new ArrayList<>(normalized);
    }

    private void validateBranch(AiSecurityContext context, Integer branchId) {
        securityContextResolver.validateBranchAccess(context, branchId == null ? null : branchId.longValue());
    }

    private void require(AiSecurityContext context, Integer branchId, String capability) {
        authorizationService.assertAuthenticatedCapability(
                context.username(),
                Math.toIntExact(context.companyId()),
                branchId,
                capability
        );
    }
}
