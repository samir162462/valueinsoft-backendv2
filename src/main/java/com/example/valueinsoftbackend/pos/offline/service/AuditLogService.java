package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.repository.SyncAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for recording audit logs for the POS offline synchronization lifecycle.
 * It ensures that logging failures do not interrupt the primary synchronization workflow.
 */
@Service
@Slf4j
public class AuditLogService {

    private final SyncAuditLogRepository auditRepo;

    /**
     * Constructs a new AuditLogService with the required repository.
     *
     * @param auditRepo the repository for synchronization audit logs
     */
    public AuditLogService(SyncAuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    /**
     * Records a synchronization event to the audit log.
     *
     * @param companyId            the company ID
     * @param branchId             the branch ID
     * @param syncBatchId          the ID of the associated sync batch (optional)
     * @param offlineOrderImportId the ID of the specific order import (optional)
     * @param deviceId             the device ID (optional)
     * @param cashierId            the cashier ID (optional)
     * @param eventType            the type of event (e.g., BATCH_RECEIVED)
     * @param eventMessage         a human-readable description of the event
     * @param payloadJson          the raw payload associated with the event (optional)
     */
    public void logSyncEvent(Long companyId, Long branchId,
                             Long syncBatchId, Long offlineOrderImportId,
                             Long deviceId, Long cashierId,
                             String eventType, String eventMessage,
                             String payloadJson) {
        try {
            auditRepo.insertAuditLog(companyId, branchId, syncBatchId,
                    offlineOrderImportId, deviceId, cashierId,
                    eventType, eventMessage, payloadJson);
        } catch (Exception e) {
            // Audit logging should never break the main flow
            log.warn("Failed to write sync audit log: eventType={}, error={}",
                    eventType, e.getMessage());
        }
    }
}
