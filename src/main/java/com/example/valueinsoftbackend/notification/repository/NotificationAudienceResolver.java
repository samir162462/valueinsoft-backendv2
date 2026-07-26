package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.AudienceMember;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Resolves a stable, user-id ordered audience from the real scope-aware assignment schema.
 * The SQL intentionally does not call a PostgreSQL function: the application's configuration
 * model stores grants in role_grants and tenant_user_grant_overrides.
 */
@Repository
public class NotificationAudienceResolver {
    private final JdbcTemplate jdbc;

    public NotificationAudienceResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int countBounded(long companyId,
                            Integer branchId,
                            String requiredCapability,
                            int limit) {
        String sql = "SELECT COUNT(*) FROM (" + audienceSql() + " LIMIT ?) bounded";
        Integer count = jdbc.queryForObject(sql, Integer.class,
                companyId, branchId, branchId,
                requiredCapability,
                companyId, requiredCapability, branchId,
                companyId, requiredCapability, branchId,
                companyId, requiredCapability, branchId,
                0, limit);
        return count == null ? 0 : count;
    }

    public List<AudienceMember> fetchBatch(long companyId,
                                           Integer branchId,
                                           String requiredCapability,
                                           int cursor,
                                           int limit) {
        String sql = """
                SELECT audience.id,
                       COALESCE((
                           SELECT nd.locale FROM public.notification_device nd
                           WHERE nd.user_id = audience.id AND nd.company_id = ?
                             AND nd.status = 'active'
                           ORDER BY nd.last_seen_at DESC, nd.device_id DESC LIMIT 1
                       ), 'en') AS locale
                FROM (%s LIMIT ?) audience
                """.formatted(audienceSql());
        return jdbc.query(sql, (rs, rowNum) ->
                        new AudienceMember(rs.getInt("id"), rs.getString("locale")),
                companyId,
                companyId, branchId, branchId,
                requiredCapability,
                companyId, requiredCapability, branchId,
                companyId, requiredCapability, branchId,
                companyId, requiredCapability, branchId,
                cursor, limit);
    }

    public boolean userHasCapability(long companyId,
                                     int userId,
                                     Integer branchId,
                                     String capability) {
        if (capability == null) {
            return true;
        }
        String sql = """
                SELECT
                  (
                    EXISTS (
                      SELECT 1
                      FROM public.tenant_user_grant_overrides o
                      WHERE o.tenant_id = ? AND o.user_id = ? AND o.capability_key = ?
                        AND o.grant_mode = 'allow'
                        AND (o.scope_type <> 'branch' OR o.scope_branch_id = ?)
                    )
                    OR EXISTS (
                      SELECT 1
                      FROM public.tenant_role_assignments a
                      JOIN public.role_grants g ON g.role_id = a.role_id
                      WHERE a.tenant_id = ? AND a.user_id = ? AND a.status = 'active'
                        AND g.capability_key = ? AND g.grant_mode = 'allow'
                        AND (a.scope_type = 'company' OR a.scope_branch_id = ?)
                    )
                  )
                  AND NOT EXISTS (
                    SELECT 1
                    FROM public.tenant_user_grant_overrides o
                    WHERE o.tenant_id = ? AND o.user_id = ? AND o.capability_key = ?
                      AND o.grant_mode = 'deny'
                      AND (o.scope_type <> 'branch' OR o.scope_branch_id = ?)
                  )
                """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class,
                companyId, userId, capability, branchId,
                companyId, userId, capability, branchId,
                companyId, userId, capability, branchId));
    }

    private static String audienceSql() {
        return """
                SELECT DISTINCT u.id
                FROM public.users u
                JOIN public.tenant_role_assignments a ON a.user_id = u.id
                WHERE a.tenant_id = ?
                  AND a.status = 'active'
                  AND (CAST(? AS INTEGER) IS NULL
                       OR a.scope_type = 'company' OR a.scope_branch_id = ?)
                  AND (
                    CAST(? AS TEXT) IS NULL
                    OR (
                      (
                        EXISTS (
                          SELECT 1 FROM public.tenant_user_grant_overrides o
                          WHERE o.tenant_id = ? AND o.user_id = u.id
                            AND o.capability_key = ? AND o.grant_mode = 'allow'
                            AND (o.scope_type <> 'branch' OR o.scope_branch_id = ?)
                        )
                        OR EXISTS (
                          SELECT 1 FROM public.tenant_role_assignments ra
                          JOIN public.role_grants g ON g.role_id = ra.role_id
                          WHERE ra.tenant_id = ? AND ra.user_id = u.id
                            AND ra.status = 'active' AND g.capability_key = ?
                            AND g.grant_mode = 'allow'
                            AND (ra.scope_type = 'company' OR ra.scope_branch_id = ?)
                        )
                      )
                      AND NOT EXISTS (
                        SELECT 1 FROM public.tenant_user_grant_overrides d
                        WHERE d.tenant_id = ? AND d.user_id = u.id
                          AND d.capability_key = ? AND d.grant_mode = 'deny'
                          AND (d.scope_type <> 'branch' OR d.scope_branch_id = ?)
                      )
                    )
                  )
                  AND u.id > ?
                ORDER BY u.id
                """;
    }
}
