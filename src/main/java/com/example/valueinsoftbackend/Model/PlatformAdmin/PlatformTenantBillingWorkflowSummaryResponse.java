package com.example.valueinsoftbackend.Model.PlatformAdmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTenantBillingWorkflowSummaryResponse {
    private int tenantId;
    private PlatformBillingHealthSnapshotResponse billingHealthSnapshot;
    private int totalBillingSupportNotes;
    private int restrictedBillingSupportNotes;
    private ArrayList<PlatformAuditEventItem> recentBillingAuditEvents;
    private ArrayList<PlatformSupportNoteItem> recentBillingSupportNotes;
    private Timestamp generatedAt;
}
