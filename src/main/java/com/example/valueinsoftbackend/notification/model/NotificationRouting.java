package com.example.valueinsoftbackend.notification.model;

import java.time.Instant;
import java.util.List;

public final class NotificationRouting {
    private NotificationRouting() {
    }

    public record Target(String kind, Integer userId, String roleId) {
    }

    public record EventRoute(
            String typeKey,
            String displayName,
            String moduleId,
            String category,
            String priority,
            String requiredCapability,
            boolean explicit,
            List<Target> targets,
            Instant updatedAt
    ) {
    }

    public record UserOption(
            int userId,
            String userName,
            String displayName
    ) {
    }

    public record RoleOption(
            String roleId,
            String displayName
    ) {
    }

    public record Dashboard(
            List<EventRoute> events,
            List<UserOption> users,
            List<RoleOption> roles
    ) {
    }
}
