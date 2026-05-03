package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.repository.PosIdempotencyRepository;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.PosIdempotencyModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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

    public IdempotencyClaimResult claimIdempotencyKey(Long companyId, Long branchId, Long deviceId,
                                                      String idempotencyKey, String offlineOrderNo,
                                                      String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new OfflineSyncException("MISSING_IDEMPOTENCY_KEY", "idempotencyKey is required");
        }
        try {
            idempotencyRepo.insertReceivedKey(companyId, branchId, deviceId, idempotencyKey, offlineOrderNo, requestHash);
            PosIdempotencyModel record = idempotencyRepo.findByKey(companyId, branchId, deviceId, idempotencyKey)
                    .orElseThrow(() -> new OfflineSyncException(
                            "IDEMPOTENCY_CLAIM_NOT_FOUND",
                            "Idempotency claim was inserted but could not be read"));
            return new IdempotencyClaimResult(record, true, true);
        } catch (DuplicateKeyException ex) {
            PosIdempotencyModel existing = idempotencyRepo.findByKey(companyId, branchId, deviceId, idempotencyKey)
                    .orElseThrow(() -> new OfflineSyncException(
                            "IDEMPOTENCY_DUPLICATE_NOT_FOUND",
                            "Duplicate idempotency key was detected but existing record could not be read"));
            boolean matches = requestHash != null && requestHash.equals(existing.requestHash());
            if (!matches) {
                idempotencyRepo.markPayloadMismatch(companyId, branchId, deviceId, idempotencyKey);
            }
            return new IdempotencyClaimResult(existing, false, matches);
        }
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

    public void markPayloadMismatch(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        idempotencyRepo.markPayloadMismatch(companyId, branchId, deviceId, idempotencyKey);
        log.debug("Marked idempotency key as payload mismatch: {}", idempotencyKey);
    }

    public PosIdempotencyModel requireMatchingRecord(Long companyId, Long branchId, Long deviceId,
                                                     String idempotencyKey, String requestHash) {
        PosIdempotencyModel existing = idempotencyRepo.findByKey(companyId, branchId, deviceId, idempotencyKey)
                .orElseThrow(() -> new OfflineSyncException(
                        "IDEMPOTENCY_RECORD_NOT_FOUND",
                        "Idempotency record was not found for offline import"));
        if (requestHash == null || !requestHash.equals(existing.requestHash())) {
            idempotencyRepo.markPayloadMismatch(companyId, branchId, deviceId, idempotencyKey);
            throw new OfflineSyncException(
                    "IDEMPOTENCY_PAYLOAD_MISMATCH",
                    "Offline import payload does not match the idempotency record");
        }
        return existing;
    }
}
