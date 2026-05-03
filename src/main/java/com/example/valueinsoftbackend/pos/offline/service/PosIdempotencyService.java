package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.repository.PosIdempotencyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PosIdempotencyService {

    private final PosIdempotencyRepository idempotencyRepo;

    public PosIdempotencyService(PosIdempotencyRepository idempotencyRepo) {
        this.idempotencyRepo = idempotencyRepo;
    }

    public boolean existsByKey(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        return idempotencyRepo.existsByKey(companyId, branchId, deviceId, idempotencyKey);
    }

    public void createProcessingKey(Long companyId, Long branchId, Long deviceId,
                                    String idempotencyKey, String offlineOrderNo,
                                    String requestHash) {
        idempotencyRepo.insertProcessingKey(companyId, branchId, deviceId,
                idempotencyKey, offlineOrderNo, requestHash);
        log.debug("Created processing idempotency key: {}", idempotencyKey);
    }

    public void markSynced(Long companyId, Long branchId, Long deviceId,
                           String idempotencyKey, Long officialOrderId,
                           String officialInvoiceNo) {
        idempotencyRepo.markSynced(companyId, branchId, deviceId,
                idempotencyKey, officialOrderId, officialInvoiceNo);
        log.debug("Marked idempotency key as synced: {}", idempotencyKey);
    }

    public void markFailed(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        idempotencyRepo.markFailed(companyId, branchId, deviceId, idempotencyKey);
        log.debug("Marked idempotency key as failed: {}", idempotencyKey);
    }
}
