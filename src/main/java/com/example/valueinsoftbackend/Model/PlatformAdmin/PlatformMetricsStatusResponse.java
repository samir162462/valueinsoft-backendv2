package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformMetricsStatusResponse {
    private boolean schedulerEnabled;
    private String schedulerCron;
    private String schedulerZone;
    private Timestamp latestRefreshAt;
    private String latestRefreshResultStatus;
    private String latestRefreshActorUserName;
    private String latestRefreshActionType;
    private Timestamp latestSuccessfulRefreshAt;
    private Date latestSnapshotDate;
    private int latestSnapshotTenantsRepresented;
    private Integer snapshotLagDays;
    private boolean stale;
    private Integer lastProcessedTenants;
    private Integer lastProcessedBranches;
    private ArrayList<Integer> lastFailedTenantIds;
}
