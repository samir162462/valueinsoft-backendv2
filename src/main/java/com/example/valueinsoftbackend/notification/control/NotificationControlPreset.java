package com.example.valueinsoftbackend.notification.control;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator presets for the control screen (§16.2).
 *
 * <p>Operators reach for intent, not a switch matrix. "Something is wrong, stop the pushes"
 * is a decision; working out that it means DISPATCH off, PUSH off, suppression CANCEL is
 * not — and getting it wrong at 2am is how the wrong thing gets turned off.
 *
 * <p>Each preset declares exactly which components it changes, so the screen can show the
 * diff before it is applied. A preset that silently changes something the operator did not
 * expect is worse than no preset.
 */
public enum NotificationControlPreset {

    /**
     * Off-hours cost reduction: everything parked so PostgreSQL can suspend.
     *
     * <p>This is the one that loses data — with the module off, {@code NotificationPublisher}
     * writes nothing and there is no backfill on resume. The screen must say so in those
     * words and require a typed confirmation (§16.3).
     */
    RESOURCE_SAVER(
            "Resource saver",
            "Parks every worker and stops the publisher so the database can suspend. "
                    + "Notifications raised while this is on are NOT recorded and cannot be recovered.",
            "SUPPRESS",
            true,
            Map.of(NotificationComponent.MODULE, false)),

    /**
     * Stop outbound push while keeping the in-app feed accurate. The normal choice during a
     * provider incident, and the one operators actually want most of the time.
     */
    QUIET_MODE(
            "Quiet mode",
            "Stops all push delivery. The in-app feed keeps working and stays accurate.",
            "SUPPRESS",
            false,
            Map.of(NotificationComponent.PUSH, false)),

    /**
     * Planned maintenance: pushes accumulate and drain when dispatch is re-enabled. Uses
     * QUEUE rather than SUPPRESS — that is the whole difference from quiet mode.
     */
    FREEZE_DELIVERY(
            "Freeze delivery",
            "Holds pushes in the queue and delivers them when dispatch is re-enabled. "
                    + "Anything older than its TTL is discarded on resume rather than delivered late.",
            "QUEUE",
            false,
            Map.of(NotificationComponent.DISPATCH, false)),

    /**
     * Incident stop: wrong notifications are going out and must stop now, with a full audit
     * trail of what was cancelled.
     */
    INCIDENT_STOP(
            "Incident stop",
            "Stops everything immediately and marks queued pushes cancelled with an audit trail.",
            "CANCEL",
            true,
            Map.of(NotificationComponent.MODULE, false)),

    /** Users can still read their history; nothing new is produced. */
    READ_ONLY(
            "Read only",
            "Stops producing and delivering notifications. Users can still read existing history.",
            "SUPPRESS",
            false,
            buildReadOnly());

    private static Map<NotificationComponent, Boolean> buildReadOnly() {
        Map<NotificationComponent, Boolean> changes = new LinkedHashMap<>();
        changes.put(NotificationComponent.PUBLISH, false);
        changes.put(NotificationComponent.FANOUT, false);
        changes.put(NotificationComponent.DISPATCH, false);
        changes.put(NotificationComponent.BROADCAST_PLANNING, false);
        changes.put(NotificationComponent.BROADCAST_MATERIALIZE, false);
        // FEED_READ stays on — that is what "read only" means.
        return changes;
    }

    private final String displayName;
    private final String description;
    private final String suppressionMode;
    private final boolean dataLoss;
    private final Map<NotificationComponent, Boolean> changes;

    NotificationControlPreset(String displayName, String description, String suppressionMode,
                              boolean dataLoss, Map<NotificationComponent, Boolean> changes) {
        this.displayName = displayName;
        this.description = description;
        this.suppressionMode = suppressionMode;
        this.dataLoss = dataLoss;
        this.changes = changes;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String suppressionMode() {
        return suppressionMode;
    }

    /** True when applying this preset means notifications are lost rather than delayed. */
    public boolean dataLoss() {
        return dataLoss;
    }

    public Map<NotificationComponent, Boolean> changes() {
        return changes;
    }

    /** Everything a preset would change, for the confirm dialog's diff. */
    public List<String> affectedComponents() {
        return changes.keySet().stream().map(NotificationComponent::key).toList();
    }
}
