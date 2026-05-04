package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.repository.PosIdempotencyRepository;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import com.example.valueinsoftbackend.pos.offline.model.PosIdempotencyModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing idempotency keys for POS offline orders.
 * It ensures that duplicate orders are not processed and that payload integrity is maintained.
 */
@Service
@Slf4j
public class PosIdempotencyService {

    private final PosIdempotencyRepository idempotencyRepo;

    /**
     * Constructs a new PosIdempotencyService with the required repository.
     *
     * @param idempotencyRepo the repository for idempotency records
     */
    public PosIdempotencyService(PosIdempotencyRepository idempotencyRepo) {
        this.idempotencyRepo = idempotencyRepo;
    }

    /**
     * Checks if an idempotency key already exists for a specific device.
     *
     * @param companyId      the company ID
     * @param branchId       the branch ID
     * @param deviceId       the device ID
     * @param idempotencyKey the idempotency key to check
     * @return true if the key exists, false otherwise
     */
    public boolean existsByKey(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        return idempotencyRepo.existsByKey(companyId, branchId, deviceId, idempotencyKey);
    }

    /**
     * Creates a new idempotency record in the PROCESSING status.
     *
     * @param companyId      the company ID
     * @param branchId       the branch ID
     * @param deviceId       the device ID
     * @param idempotencyKey the unique idempotency key
     * @param offlineOrderNo the local offline order number
     * @param requestHash    the hash of the order payload
     */
    public void createProcessingKey(Long companyId, Long branchId, Long deviceId,
                                    String idempotencyKey, String offlineOrderNo,
                                    String requestHash) {
        idempotencyRepo.insertProcessingKey(companyId, branchId, deviceId,
                idempotencyKey, offlineOrderNo, requestHash);
        log.debug("Created processing idempotency key: {}", idempotencyKey);
    }

    /**
     * Attempts to claim an idempotency key. If the key exists, it checks for payload integrity.
     * If it doesn't exist, it inserts a new RECEIVED record.
     *
     * @param companyId      the company ID
     * @param branchId       the branch ID
     * @param deviceId       the device ID
     * @param idempotencyKey the unique idempotency key
     * @param offlineOrderNo the local offline order number
     * @param requestHash    the hash of the order payload
     * @return a {@link IdempotencyClaimResult} containing the record and matching status
     * @throws OfflineSyncException if the key is missing or cannot be read after insertion
     */
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

    /**
     * Marks an idempotency key as successfully synced with an official order ID.
     *
     * @param companyId         the company ID
     * @param branchId          the branch ID
     * @param deviceId          the device ID
     * @param idempotencyKey    the unique idempotency key
     * @param officialOrderId   the ID of the resulting official order
     * @param officialInvoiceNo the resulting invoice number (optional)
     */
    public void markSynced(Long companyId, Long branchId, Long deviceId,
                           String idempotencyKey, Long officialOrderId,
                           String officialInvoiceNo) {
        idempotencyRepo.markSynced(companyId, branchId, deviceId,
                idempotencyKey, officialOrderId, officialInvoiceNo);
        log.debug("Marked idempotency key as synced: {}", idempotencyKey);
    }

    /**
     * Marks an idempotency key as failed.
     *
     * @param companyId      the company ID
     * @param branchId       the branch ID
     * @param deviceId       the device ID
     * @param idempotencyKey the unique idempotency key
     */
    public void markFailed(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        idempotencyRepo.markFailed(companyId, branchId, deviceId, idempotencyKey);
        log.debug("Marked idempotency key as failed: {}", idempotencyKey);
    }

    /**
     * Marks an idempotency key as having a payload mismatch (hash mismatch).
     *
     * @param companyId      the company ID
     * @param branchId       the branch ID
     * @param deviceId       the device ID
     * @param idempotencyKey the unique idempotency key
     */
    public void markPayloadMismatch(Long companyId, Long branchId, Long deviceId, String idempotencyKey) {
        idempotencyRepo.markPayloadMismatch(companyId, branchId, deviceId, idempotencyKey);
        log.debug("Marked idempotency key as payload mismatch: {}", idempotencyKey);
    }

    /**
     * Finds an existing record and verifies that the payload hash matches.
     *
     * @param companyId      the company ID
     * @param branchId       the branch ID
     * @param deviceId       the device ID
     * @param idempotencyKey the unique idempotency key
     * @param requestHash    the expected payload hash
     * @return the verified {@link PosIdempotencyModel}
     * @throws OfflineSyncException if the record is missing or the hash does not match
     */
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
