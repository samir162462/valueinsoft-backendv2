package com.example.valueinsoftbackend.notification.service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.Dashboard;
import com.example.valueinsoftbackend.notification.model.NotificationRouting.Target;
import com.example.valueinsoftbackend.notification.repository.DbNotificationCatalog;
import com.example.valueinsoftbackend.notification.repository.DbNotificationRouting;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class NotificationRoutingService {
    private final DbNotificationCatalog catalog;
    private final DbNotificationRouting routing;

    public NotificationRoutingService(DbNotificationCatalog catalog,
                                      DbNotificationRouting routing) {
        this.catalog = catalog;
        this.routing = routing;
    }

    public Dashboard dashboard(long companyId) {
        return routing.dashboard(companyId);
    }

    @Transactional
    public Dashboard update(long companyId, int actorUserId, List<String> typeKeys,
                            boolean useDefault, List<Target> requestedTargets) {
        LinkedHashSet<String> normalizedTypes = new LinkedHashSet<>();
        for (String typeKey : typeKeys == null ? List.<String>of() : typeKeys) {
            if (typeKey == null || typeKey.isBlank()) {
                continue;
            }
            String normalized = typeKey.trim();
            try {
                catalog.requireActive(normalized);
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "INVALID_NOTIFICATION_TYPE",
                        "Unknown or inactive notification event type: " + normalized);
            }
            normalizedTypes.add(normalized);
        }
        if (normalizedTypes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOTIFICATION_TYPES_REQUIRED",
                    "Select at least one notification event type");
        }

        if (useDefault) {
            normalizedTypes.forEach(typeKey -> routing.delete(companyId, typeKey));
            return routing.dashboard(companyId);
        }

        List<Target> targets = normalizeTargets(requestedTargets);
        validateTargets(companyId, targets);
        normalizedTypes.forEach(typeKey ->
                routing.replace(companyId, typeKey, actorUserId, targets));
        return routing.dashboard(companyId);
    }

    private List<Target> normalizeTargets(List<Target> requested) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        List<Target> result = new ArrayList<>();
        for (Target target : requested == null ? List.<Target>of() : requested) {
            String kind = target == null || target.kind() == null
                    ? "" : target.kind().trim().toLowerCase(Locale.ROOT);
            if ("user".equals(kind) && target.userId() != null && target.userId() > 0) {
                if (keys.add("user:" + target.userId())) {
                    result.add(new Target("user", target.userId(), null));
                }
            } else if ("role".equals(kind) && target.roleId() != null
                    && !target.roleId().isBlank()) {
                String roleId = target.roleId().trim();
                if (keys.add("role:" + roleId)) {
                    result.add(new Target("role", null, roleId));
                }
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "INVALID_NOTIFICATION_ROUTE_TARGET",
                        "Each target must reference either a user or a role");
            }
        }
        return List.copyOf(result);
    }

    private void validateTargets(long companyId, List<Target> targets) {
        Set<Integer> userIds = targets.stream()
                .filter(target -> "user".equals(target.kind()))
                .map(Target::userId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> roleIds = targets.stream()
                .filter(target -> "role".equals(target.kind()))
                .map(Target::roleId)
                .collect(java.util.stream.Collectors.toSet());

        if (!routing.activeUserIds(companyId, userIds).equals(userIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_NOTIFICATION_ROUTE_USER",
                    "One or more selected users are not active in this company");
        }
        if (!routing.activeRoleIds(companyId, roleIds).equals(roleIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_NOTIFICATION_ROUTE_ROLE",
                    "One or more selected roles are not active in this company");
        }
    }
}
