package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.repository.SyncAuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuditLogService {

    private final SyncAuditLogRepository auditRepo;

    public AuditLogService(SyncAuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

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
