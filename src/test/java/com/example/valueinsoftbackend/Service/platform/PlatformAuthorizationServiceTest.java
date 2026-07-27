package com.example.valueinsoftbackend.Service.platform;

import com.example.valueinsoftbackend.DatabaseRequests.DbPlatformCapabilities;
import com.example.valueinsoftbackend.DatabaseRequests.DbRoleGrants;
import com.example.valueinsoftbackend.DatabaseRequests.DbUsers;
import com.example.valueinsoftbackend.Model.Configuration.PlatformCapabilityConfig;
import com.example.valueinsoftbackend.Model.Configuration.RoleGrantConfig;
import com.example.valueinsoftbackend.Model.PlatformAdmin.PlatformSessionAccessResponse;
import com.example.valueinsoftbackend.Model.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlatformAuthorizationServiceTest {

    @Test
    void platformAccessReturnsOnlyActiveAllowedGlobalGrants() {
        DbPlatformCapabilities capabilities = mock(DbPlatformCapabilities.class);
        DbRoleGrants roleGrants = mock(DbRoleGrants.class);
        DbUsers users = mock(DbUsers.class);
        User user = mock(User.class);
        when(user.getUserName()).thenReturn("support");
        when(user.getRole()).thenReturn("SupportAdmin");

        when(users.getUser("support")).thenReturn(user);
        when(capabilities.getActiveCapabilities()).thenReturn(List.of(
                capability("platform.admin.read", "web_admin", "global_admin"),
                capability("tenant.setup.manage", "company", "tenant"),
                capability("platform.retired", "web_admin", "global_admin")));
        when(roleGrants.getGrantsForRoleIds(List.of("SupportAdmin"))).thenReturn(List.of(
                grant("platform.admin.read", "global_admin", "allow"),
                grant("platform.retired", "global_admin", "deny"),
                grant("tenant.setup.manage", "tenant", "allow"),
                grant("missing.capability", "global_admin", "allow")));

        PlatformAuthorizationService service =
                new PlatformAuthorizationService(capabilities, roleGrants, users);

        PlatformSessionAccessResponse response =
                service.getPlatformAccess("support : SupportAdmin");

        assertThat(response.userName()).isEqualTo("support");
        assertThat(response.role()).isEqualTo("SupportAdmin");
        assertThat(response.capabilityKeys()).containsExactly("platform.admin.read");
        assertThat(response.moduleIds()).containsExactly("web_admin");
    }

    private static PlatformCapabilityConfig capability(
            String key, String module, String scope) {
        return new PlatformCapabilityConfig(
                key, module, "admin", "read", scope, "active", key);
    }

    private static RoleGrantConfig grant(String key, String scope, String mode) {
        return new RoleGrantConfig("SupportAdmin", key, scope, mode, "v1");
    }
}
