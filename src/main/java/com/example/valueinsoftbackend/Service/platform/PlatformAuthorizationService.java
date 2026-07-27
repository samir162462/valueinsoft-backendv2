package com.example.valueinsoftbackend.Service.platform;

import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformCapabilities;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleGrants;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSessionAccessResponse;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlatformAuthorizationService {

    private final DbPlatformCapabilities dbPlatformCapabilities;
    private final DbRoleGrants dbRoleGrants;
    private final DbUsers dbUsers;

    public PlatformAuthorizationService(DbPlatformCapabilities dbPlatformCapabilities,
                                        DbRoleGrants dbRoleGrants,
                                        DbUsers dbUsers) {
        this.dbPlatformCapabilities = dbPlatformCapabilities;
        this.dbRoleGrants = dbRoleGrants;
        this.dbUsers = dbUsers;
    }

    public User requirePlatformCapability(String authenticatedName, String capabilityKey) {
        User user = requireUser(authenticatedName);

        PlatformCapabilityConfig capability = dbPlatformCapabilities.getCapability(capabilityKey);
        if (capability == null || !"active".equalsIgnoreCase(capability.getStatus())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "PLATFORM_CAPABILITY_NOT_FOUND",
                    "Platform capability not found: " + capabilityKey
            );
        }

        List<RoleGrantConfig> grants = dbRoleGrants.getGrantsForRoleIds(List.of(user.getRole()));
        for (RoleGrantConfig grant : grants) {
            if (capabilityKey.equals(grant.getCapabilityKey())
                    && "allow".equalsIgnoreCase(grant.getGrantMode())
                    && "global_admin".equalsIgnoreCase(grant.getScopeType())) {
                return user;
            }
        }

        throw new ApiException(
                HttpStatus.FORBIDDEN,
                "PLATFORM_CAPABILITY_DENIED",
                "Missing required platform capability: " + capabilityKey
        );

    }

    /**
     * Returns only the authenticated user's active, allowed global-admin grants.
     *
     * <p>No tenant context is accepted or inferred here. This is the bootstrap used by
     * platform-only sessions before a WebAdmin route can be authorized.
     */
    public PlatformSessionAccessResponse getPlatformAccess(String authenticatedName) {
        User user = requireUser(authenticatedName);
        Map<String, PlatformCapabilityConfig> activeCapabilities =
                dbPlatformCapabilities.getActiveCapabilities().stream()
                        .filter(capability ->
                                "global_admin".equalsIgnoreCase(capability.getScopeType()))
                        .collect(Collectors.toMap(
                                PlatformCapabilityConfig::getCapabilityKey,
                                Function.identity(),
                                (left, right) -> left));

        Set<String> grantedKeys = dbRoleGrants.getGrantsForRoleIds(List.of(user.getRole())).stream()
                .filter(grant -> "global_admin".equalsIgnoreCase(grant.getScopeType()))
                .filter(grant -> "allow".equalsIgnoreCase(grant.getGrantMode()))
                .map(RoleGrantConfig::getCapabilityKey)
                .filter(activeCapabilities::containsKey)
                .collect(Collectors.toCollection(java.util.TreeSet::new));

        List<String> moduleIds = grantedKeys.stream()
                .map(activeCapabilities::get)
                .map(PlatformCapabilityConfig::getModuleId)
                .filter(moduleId -> moduleId != null && !moduleId.isBlank())
                .distinct()
                .sorted()
                .toList();

        return new PlatformSessionAccessResponse(
                user.getUserName(),
                user.getRole(),
                List.copyOf(grantedKeys),
                moduleIds);
    }

    private User requireUser(String authenticatedName) {
        String userName = extractBaseUserName(authenticatedName);
        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
        return user;
    }

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }
}
