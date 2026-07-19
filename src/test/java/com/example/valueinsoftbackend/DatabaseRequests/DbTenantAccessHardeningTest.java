package com.example.valueinsoftbackend.DatabaseRequests;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbTenantAccessHardeningTest {

    @Test
    void roleAssignmentDeactivationIncludesTenantPredicate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DbConfigurationAdmin repository = new DbConfigurationAdmin(jdbcTemplate);

        when(jdbcTemplate.update(
                eq("UPDATE public.tenant_role_assignments SET status = 'inactive' " +
                        "WHERE tenant_id = ? AND assignment_id = ?"),
                eq(12),
                eq(99L)
        )).thenReturn(0);

        repository.deactivateTenantRoleAssignment(12, 99L);

        verify(jdbcTemplate).update(
                eq("UPDATE public.tenant_role_assignments SET status = 'inactive' " +
                        "WHERE tenant_id = ? AND assignment_id = ?"),
                eq(12),
                eq(99L)
        );
    }

    @Test
    void accessOverrideAuditWritesAllSecurityContextFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DbTenantAccessAuditEvents repository = new DbTenantAccessAuditEvents(jdbcTemplate);

        repository.insertEvent(
                12,
                7,
                41,
                "inventory.item.edit",
                "OVERRIDE_UPSERTED",
                "deny",
                "branch",
                3,
                "temporary_block"
        );

        verify(jdbcTemplate).update(
                eq("INSERT INTO public.tenant_access_audit_events " +
                        "(tenant_id, actor_user_id, target_user_id, capability_key, action, grant_mode, " +
                        "scope_type, scope_branch_id, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"),
                eq(12),
                eq(7),
                eq(41),
                eq("inventory.item.edit"),
                eq("OVERRIDE_UPSERTED"),
                eq("deny"),
                eq("branch"),
                eq(3),
                eq("temporary_block")
        );
    }
}

