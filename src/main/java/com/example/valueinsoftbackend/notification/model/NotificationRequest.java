package com.example.valueinsoftbackend.notification.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Immutable input to the notification event boundary. Audience resolution and rendering are
 * deliberately absent: publishing only records an event and one fan-out job.
 */
public record NotificationRequest(
        long companyId,
        String typeKey,
        String idempotencyKey,
        Integer branchId,
        Integer actorUserId,
        String subjectType,
        Long subjectId,
        Map<String, Object> params,
        String priority,
        String groupKey,
        String source,
        Long broadcastId,
        String correlationId
) {
    private static final Pattern TYPE_KEY =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$");
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}$");

    public NotificationRequest {
        if (companyId <= 0) {
            throw new IllegalArgumentException("companyId must be positive");
        }
        typeKey = requirePattern(typeKey, TYPE_KEY, "typeKey");
        idempotencyKey = requirePattern(idempotencyKey, IDEMPOTENCY_KEY, "idempotencyKey");
        subjectType = trimToNull(subjectType);
        if ((subjectType == null) != (subjectId == null)) {
            throw new IllegalArgumentException("subjectType and subjectId must be supplied together");
        }
        if (branchId != null && branchId <= 0) {
            throw new IllegalArgumentException("branchId must be positive");
        }
        if (actorUserId != null && actorUserId <= 0) {
            throw new IllegalArgumentException("actorUserId must be positive");
        }
        if (broadcastId != null && broadcastId <= 0) {
            throw new IllegalArgumentException("broadcastId must be positive");
        }
        source = source == null ? "system" : source.trim().toLowerCase();
        if (!source.equals("system") && !source.equals("broadcast")) {
            throw new IllegalArgumentException("source must be system or broadcast");
        }
        if (source.equals("broadcast") && broadcastId == null) {
            throw new IllegalArgumentException("broadcast source requires broadcastId");
        }
        priority = trimToNull(priority);
        if (priority != null && !java.util.Set.of("critical", "high", "normal", "low").contains(priority)) {
            throw new IllegalArgumentException("invalid priority");
        }
        groupKey = trimToNull(groupKey);
        correlationId = trimToNull(correlationId);
        params = Collections.unmodifiableMap(
                params == null ? Map.of() : new LinkedHashMap<>(params));
    }

    public static Builder builder(long companyId, String typeKey, String idempotencyKey) {
        return new Builder(companyId, typeKey, idempotencyKey);
    }

    private static String requirePattern(String value, Pattern pattern, String field) {
        String normalized = Objects.requireNonNull(value, field + " is required").trim();
        if (!pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid " + field);
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static final class Builder {
        private final long companyId;
        private final String typeKey;
        private final String idempotencyKey;
        private Integer branchId;
        private Integer actorUserId;
        private String subjectType;
        private Long subjectId;
        private Map<String, Object> params = Map.of();
        private String priority;
        private String groupKey;
        private String source = "system";
        private Long broadcastId;
        private String correlationId;

        private Builder(long companyId, String typeKey, String idempotencyKey) {
            this.companyId = companyId;
            this.typeKey = typeKey;
            this.idempotencyKey = idempotencyKey;
        }

        public Builder branchId(Integer value) { branchId = value; return this; }
        public Builder actorUserId(Integer value) { actorUserId = value; return this; }
        public Builder subject(String type, Long id) { subjectType = type; subjectId = id; return this; }
        public Builder params(Map<String, Object> value) { params = value; return this; }
        public Builder priority(String value) { priority = value; return this; }
        public Builder groupKey(String value) { groupKey = value; return this; }
        public Builder source(String value) { source = value; return this; }
        public Builder broadcastId(Long value) { broadcastId = value; return this; }
        public Builder correlationId(String value) { correlationId = value; return this; }

        public NotificationRequest build() {
            return new NotificationRequest(companyId, typeKey, idempotencyKey, branchId,
                    actorUserId, subjectType, subjectId, params, priority, groupKey, source,
                    broadcastId, correlationId);
        }
    }
}
