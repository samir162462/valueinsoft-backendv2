package com.example.valueinsoftbackend.Model.Response;

import java.time.Instant;

public class SupplierAuditEventResponse {

    private final Instant eventTime;
    private final String eventType;
    private final String actor;
    private final String sourceType;
    private final String sourceId;
    private final String summary;
    private final String changes;

    public SupplierAuditEventResponse(Instant eventTime,
                                      String eventType,
                                      String actor,
                                      String sourceType,
                                      String sourceId,
                                      String summary,
                                      String changes) {
        this.eventTime = eventTime;
        this.eventType = eventType;
        this.actor = actor;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.summary = summary;
        this.changes = changes;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public String getEventType() {
        return eventType;
    }

    public String getActor() {
        return actor;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSummary() {
        return summary;
    }

    public String getChanges() {
        return changes;
    }
}
