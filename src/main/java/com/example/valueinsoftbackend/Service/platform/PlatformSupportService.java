package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.DatabaseRequests.DbCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformAdminOperations;
import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformSupportNotes;
import com.example.valueinsoftbackend.DatabaseRequests.DbTenants;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.Configuration.TenantConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupportNoteItem;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSupportNotesPageResponse;
import com.example.valueinsoftbackend.Model.Request.PlatformAdmin.CreatePlatformSupportNoteRequest;
import com.example.valueinsoftbackend.Model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

@Service
public class PlatformSupportService {

    private static final Set<String> ALLOWED_NOTE_TYPES = Set.of("support", "billing", "risk", "ops", "follow_up");
    private static final Set<String> ALLOWED_VISIBILITY = Set.of("internal", "restricted");

    private final PlatformAuthorizationService platformAuthorizationService;
    private final DbPlatformSupportNotes dbPlatformSupportNotes;
    private final DbPlatformAdminOperations dbPlatformAdminOperations;
    private final DbTenants dbTenants;
    private final DbCompany dbCompany;
    private final DbBranch dbBranch;
    private final ObjectMapper objectMapper;

    public PlatformSupportService(PlatformAuthorizationService platformAuthorizationService,
                                  DbPlatformSupportNotes dbPlatformSupportNotes,
                                  DbPlatformAdminOperations dbPlatformAdminOperations,
                                  DbTenants dbTenants,
                                  DbCompany dbCompany,
                                  DbBranch dbBranch,
                                  ObjectMapper objectMapper) {
        this.platformAuthorizationService = platformAuthorizationService;
        this.dbPlatformSupportNotes = dbPlatformSupportNotes;
        this.dbPlatformAdminOperations = dbPlatformAdminOperations;
        this.dbTenants = dbTenants;
        this.dbCompany = dbCompany;
        this.dbBranch = dbBranch;
        this.objectMapper = objectMapper;
    }

    public PlatformSupportNotesPageResponse getNotesForAuthenticatedUser(String authenticatedName,
                                                                         Integer tenantId,
                                                                         Integer branchId,
                                                                         String noteType,
                                                                         String visibility,
                                                                         int page,
                                                                         int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.support.read");
        if (tenantId != null) {
            requireTenant(tenantId);
        }
        if (branchId != null) {
            Branch branch = dbBranch.getBranchById(branchId);
            if (tenantId != null && branch.getBranchOfCompanyId() != tenantId) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SUPPORT_NOTE_BRANCH_SCOPE_INVALID",
                        "Branch does not belong to the requested tenant"
                );
            }
        }
        return dbPlatformSupportNotes.getSupportNotes(
                tenantId,
                branchId,
                normalizeNullable(noteType),
                normalizeNullable(visibility),
                page,
                size
        );
    }

    public PlatformSupportNotesPageResponse getTenantNotesForAuthenticatedUser(String authenticatedName,
                                                                               int tenantId,
                                                                               int page,
                                                                               int size) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.support.read");
        requireTenant(tenantId);
        return dbPlatformSupportNotes.getSupportNotes(tenantId, null, null, null, page, size);
    }

    public ArrayList<PlatformSupportNoteItem> getRecentBillingNotesForAuthenticatedUser(String authenticatedName,
                                                                                         int tenantId,
                                                                                         int limit) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.support.read");
        requireTenant(tenantId);
        return dbPlatformSupportNotes.getRecentBillingNotes(tenantId, limit);
    }

    public int countBillingNotesForAuthenticatedUser(String authenticatedName,
                                                     int tenantId,
                                                     String visibility) {
        platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.support.read");
        requireTenant(tenantId);
        return dbPlatformSupportNotes.countBillingNotes(tenantId, visibility);
    }

    @Transactional
    public PlatformSupportNoteItem createNoteForAuthenticatedUser(String authenticatedName,
                                                                  CreatePlatformSupportNoteRequest request) {
        User actor = platformAuthorizationService.requirePlatformCapability(authenticatedName, "platform.support.write");
        TenantConfig tenant = requireTenant(request.getTenantId());
        if (dbCompany.getCompanyById(tenant.getTenantId()) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Company not found");
        }

        Integer branchId = request.getBranchId();
        Branch branch = null;
        if (branchId != null) {
            branch = dbBranch.getBranchById(branchId);
            if (branch.getBranchOfCompanyId() != tenant.getTenantId()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SUPPORT_NOTE_BRANCH_SCOPE_INVALID",
                        "Branch does not belong to the requested tenant"
                );
            }
        }

        String noteType = normalize(request.getNoteType()).toLowerCase();
        String visibility = normalize(request.getVisibility()).toLowerCase();
        if (!ALLOWED_NOTE_TYPES.contains(noteType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPORT_NOTE_TYPE_INVALID", "Unsupported support note type");
        }
        if (!ALLOWED_VISIBILITY.contains(visibility)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPORT_NOTE_VISIBILITY_INVALID", "Unsupported support note visibility");
        }

        PlatformSupportNoteItem note = dbPlatformSupportNotes.createSupportNote(
                tenant.getTenantId(),
                branch == null ? null : branch.getBranchID(),
                noteType,
                normalize(request.getSubject()),
                normalize(request.getBody()),
                visibility,
                actor.getUserId(),
                actor.getUserName()
        );

        dbPlatformAdminOperations.insertAuditLog(
                actor.getUserId(),
                actor.getUserName(),
                "platform.support.write",
                "platform.support.note.create",
                tenant.getTenantId(),
                branch == null ? null : branch.getBranchID(),
                toJson(buildMap(
                        "noteType", noteType,
                        "visibility", visibility,
                        "subject", normalize(request.getSubject())
                )),
                toJson(buildMap(
                        "noteId", note.getNoteId(),
                        "tenantId", tenant.getTenantId(),
                        "branchId", branch == null ? null : branch.getBranchID()
                )),
                "success",
                null
        );

        return note;
    }

    private TenantConfig requireTenant(int tenantId) {
        TenantConfig tenant = dbTenants.getTenantById(tenantId);
        if (tenant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found");
        }
        return tenant;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<String, Object> buildMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private String toJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PLATFORM_SUPPORT_SERIALIZATION_FAILED",
                    "Could not serialize platform support audit payload"
            );
        }
    }
}
