package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminBillingReadModels;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSummaryResponse;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformBillingSubscriptionsPageResponse;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminBillingService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final DbPlatformAdminBillingReadModels dbPlatformAdminBillingReadModels;
    private final PlatformAuthorizationService platformAuthorizationService;

    public PlatformAdminBillingService(DbPlatformAdminBillingReadModels dbPlatformAdminBillingReadModels,
                                       PlatformAuthorizationService platformAuthorizationService) {
        this.dbPlatformAdminBillingReadModels = dbPlatformAdminBillingReadModels;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    public PlatformBillingSummaryResponse getBillingSummaryForAuthenticatedUser(String authenticatedName, String packageId) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbPlatformAdminBillingReadModels.getBillingSummary(packageId);
    }

    public PlatformBillingSubscriptionsPageResponse getLatestSubscriptionsForAuthenticatedUser(String authenticatedName,
                                                                                               String search,
                                                                                               String status,
                                                                                               String packageId,
                                                                                               Integer tenantId,
                                                                                               int page,
                                                                                               int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.billing.read");
        return dbPlatformAdminBillingReadModels.getLatestSubscriptions(
                search,
                status,
                packageId,
                tenantId,
                sanitizePage(page),
                sanitizeSize(size)
        );
    }

    private int sanitizePage(int page) {
        return Math.max(1, page);
    }

    private int sanitizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
