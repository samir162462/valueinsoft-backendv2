package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformCapabilities;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleGrants;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

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
        String userName = extractBaseUserName(authenticatedName);
        User user = dbUsers.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }

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

    private String extractBaseUserName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ")[0];
        }
        return value == null ? "" : value.trim();
    }
}
