package com.example.valueinsoftbackend.notification.repository;

import com.example.valueinsoftbackend.notification.model.NotificationRouting.Dashboard;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.EventRoute;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.RoleOption;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.Target;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.UserOption;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class DbNotificationRouting {
    private final JdbcTemplate jdbc;

    public DbNotificationRouting(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Dashboard dashboard(long companyId) {
        Map<String, List<Target>> targets = new LinkedHashMap<>();
        List<RouteTargetRow> targetRows = jdbc.query("""
                SELECT type_key, target_kind, user_id, role_id
                FROM public.notification_company_route_target
                WHERE company_id = ?
                ORDER BY target_kind, COALESCE(role_id, user_id::text)
                """, (rs, rowNum) -> new RouteTargetRow(
                        rs.getString("type_key"),
                        new Target(
                        rs.getString("target_kind"),
                        rs.getObject("user_id", Integer.class),
                        rs.getString("role_id"))), companyId);
        for (RouteTargetRow row : targetRows) {
            targets.computeIfAbsent(row.typeKey(), ignored -> new ArrayList<>())
                    .add(row.target());
        }

        List<EventRoute> events = jdbc.query("""
                SELECT c.type_key,
                       COALESCE(t.title_template, c.type_key) AS display_name,
                       c.module_id,
                       c.category,
                       c.default_priority,
                       c.required_capability,
                       (r.type_key IS NOT NULL) AS explicit,
                       r.updated_at
                FROM public.notification_type_catalog c
                LEFT JOIN public.notification_template t
                  ON t.type_key = c.type_key
                 AND t.locale = 'en'
                 AND t.status = 'published'
                LEFT JOIN public.notification_company_route r
                  ON r.company_id = ?
                 AND r.type_key = c.type_key
                WHERE c.status = 'active'
                ORDER BY c.category, c.module_id, c.type_key
                """, (rs, rowNum) -> {
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return new EventRoute(
                    rs.getString("type_key"),
                    rs.getString("display_name"),
                    rs.getString("module_id"),
                    rs.getString("category"),
                    rs.getString("default_priority"),
                    rs.getString("required_capability"),
                    rs.getBoolean("explicit"),
                    List.copyOf(targets.getOrDefault(rs.getString("type_key"), List.of())),
                    updatedAt == null ? null : updatedAt.toInstant());
        }, companyId);

        List<UserOption> users = jdbc.query("""
                SELECT DISTINCT u.id,
                       u."userName",
                       NULLIF(BTRIM(CONCAT_WS(' ', u."firstName", u."lastName")), '') AS display_name
                FROM public.users u
                JOIN public.tenant_role_assignments a
                  ON a.user_id = u.id
                 AND a.tenant_id = ?
                 AND a.status = 'active'
                ORDER BY display_name NULLS LAST, u."userName", u.id
                """, (rs, rowNum) -> new UserOption(
                rs.getInt("id"),
                rs.getString("userName"),
                rs.getString("display_name")), companyId);

        List<RoleOption> roles = jdbc.query("""
                SELECT DISTINCT r.role_id, r.display_name
                FROM public.role_definitions r
                JOIN public.tenant_role_assignments a
                  ON a.role_id = r.role_id
                 AND a.tenant_id = ?
                 AND a.status = 'active'
                WHERE r.status = 'active'
                ORDER BY r.display_name, r.role_id
                """, (rs, rowNum) -> new RoleOption(
                rs.getString("role_id"),
                rs.getString("display_name")), companyId);

        return new Dashboard(List.copyOf(events), List.copyOf(users), List.copyOf(roles));
    }

    public Set<Integer> activeUserIds(long companyId, Set<Integer> requestedIds) {
        if (requestedIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", requestedIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.addAll(requestedIds);
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT user_id
                FROM public.tenant_role_assignments
                WHERE tenant_id = ? AND status = 'active'
                  AND user_id IN (%s)
                """.formatted(placeholders), Integer.class, args.toArray()));
    }

    public Set<String> activeRoleIds(long companyId, Set<String> requestedIds) {
        if (requestedIds.isEmpty()) {
            return Set.of();
        }
        String placeholders = String.join(",", requestedIds.stream().map(id -> "?").toList());
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        args.addAll(requestedIds);
        return Set.copyOf(jdbc.queryForList("""
                SELECT DISTINCT a.role_id
                FROM public.tenant_role_assignments a
                JOIN public.role_definitions r ON r.role_id = a.role_id
                WHERE a.tenant_id = ? AND a.status = 'active' AND r.status = 'active'
                  AND a.role_id IN (%s)
                """.formatted(placeholders), String.class, args.toArray()));
    }

    public void replace(long companyId, String typeKey, int actorUserId, List<Target> targets) {
        jdbc.update("""
                INSERT INTO public.notification_company_route
                    (company_id, type_key, updated_by_user_id)
                VALUES (?, ?, ?)
                ON CONFLICT (company_id, type_key) DO UPDATE SET
                    updated_by_user_id = EXCLUDED.updated_by_user_id,
                    updated_at = NOW()
                """, companyId, typeKey, actorUserId);
        jdbc.update("""
                DELETE FROM public.notification_company_route_target
                WHERE company_id = ? AND type_key = ?
                """, companyId, typeKey);
        for (Target target : targets) {
            jdbc.update("""
                    INSERT INTO public.notification_company_route_target
                        (company_id, type_key, target_kind, user_id, role_id)
                    VALUES (?, ?, ?, ?, ?)
                    """, companyId, typeKey, target.kind(), target.userId(), target.roleId());
        }
    }

    public void delete(long companyId, String typeKey) {
        jdbc.update("""
                DELETE FROM public.notification_company_route
                WHERE company_id = ? AND type_key = ?
                """, companyId, typeKey);
    }

    public boolean hasExplicitRoute(long companyId, String typeKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM public.notification_company_route
                    WHERE company_id = ? AND type_key = ?
                )
                """, Boolean.class, companyId, typeKey));
    }

    public boolean isExplicitRecipient(long companyId, String typeKey,
                                       Integer branchId, int userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM public.notification_company_route_target target
                    WHERE target.company_id = ?
                      AND target.type_key = ?
                      AND EXISTS (
                        SELECT 1
                        FROM public.tenant_role_assignments membership
                        WHERE membership.tenant_id = target.company_id
                          AND membership.user_id = ?
                          AND membership.status = 'active'
                      )
                      AND (
                        (target.target_kind = 'user' AND target.user_id = ?)
                        OR
                        (
                          target.target_kind = 'role'
                          AND EXISTS (
                            SELECT 1
                            FROM public.tenant_role_assignments a
                            WHERE a.tenant_id = target.company_id
                              AND a.user_id = ?
                              AND a.role_id = target.role_id
                              AND a.status = 'active'
                              AND (
                                CAST(? AS INTEGER) IS NULL
                                OR a.scope_type = 'company'
                                OR a.scope_branch_id = ?
                              )
                          )
                        )
                      )
                )
                """, Boolean.class,
                companyId, typeKey, userId, userId, userId, branchId, branchId));
    }

    private record RouteTargetRow(String typeKey, Target target) {
    }
}
