package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminAudit;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformAuditEventsPageResponse;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminAuditService {

    private final DbPlatformAdminAudit dbPlatformAdminAudit;
    private final PlatformAuthorizationService platformAuthorizationService;

    public PlatformAdminAuditService(DbPlatformAdminAudit dbPlatformAdminAudit,
                                     PlatformAuthorizationService platformAuthorizationService) {
        this.dbPlatformAdminAudit = dbPlatformAdminAudit;
        this.platformAuthorizationService = platformAuthorizationService;
    }

    public PlatformAuditEventsPageResponse getAuditEventsForAuthenticatedUser(String authenticatedName,
                                                                              Integer targetTenantId,
                                                                              Integer targetBranchId,
                                                                              String actorUserName,
                                                                              String actionType,
                                                                              String resultStatus,
                                                                              int page,
                                                                              int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.audit.read");
        return dbPlatformAdminAudit.getAuditEvents(
                targetTenantId,
                targetBranchId,
                actorUserName,
                actionType,
                resultStatus,
                page,
                size
        );
    }
}
