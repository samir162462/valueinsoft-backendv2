package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.User;
import com.example.valueinsoftbackend.Service.security.AuthenticatedEffectiveConfigurationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class NotificationRequestContextResolver {
    private final DbUsers users;
    private final AuthenticatedEffectiveConfigurationService tenantContexts;

    public NotificationRequestContextResolver(
            DbUsers users,
            AuthenticatedEffectiveConfigurationService tenantContexts) {
        this.users = users;
        this.tenantContexts = tenantContexts;
    }

    public Context resolve(String principalName) {
        String userName = baseName(principalName);
        User user = users.getUser(userName);
        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found");
        }
        var tenant = tenantContexts.resolveTenantContextForAuthenticatedUser(
                principalName, null, null);
        return new Context(tenant.getTenantId(), tenant.getActiveBranchId(),
                user.getUserId(), userName);
    }

    private static String baseName(String value) {
        if (value != null && value.contains(" : ")) {
            return value.split(" : ", 2)[0].trim();
        }
        return value == null ? "" : value.trim();
    }

    public record Context(long companyId, Integer branchId, int userId, String userName) {}
}
