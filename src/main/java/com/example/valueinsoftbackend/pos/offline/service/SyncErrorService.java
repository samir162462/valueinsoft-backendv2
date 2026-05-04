package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorItemResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorListResponse;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderErrorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for managing and retrieving synchronization errors
 * related to offline POS order imports.
 */
@Service
@Slf4j
public class SyncErrorService {

    private final OfflineOrderErrorRepository errorRepo;

    /**
     * Constructs a new SyncErrorService with the required repository.
     *
     * @param errorRepo the repository for offline order errors
     */
    public SyncErrorService(OfflineOrderErrorRepository errorRepo) {
        this.errorRepo = errorRepo;
    }

    /**
     * Persists a new synchronization error.
     *
     * @param offlineOrderImportId   the ID of the associated import record
     * @param companyId              the company ID
     * @param branchId               the branch ID
     * @param errorStage             the stage where the error occurred (e.g., VALIDATION)
     * @param errorCode              the specific error code
     * @param errorMessage           a human-readable error message
     * @param fieldPath              the JSON path to the problematic field (optional)
     * @param rawValue               the raw value that caused the error (optional)
     * @param severity               the severity level of the error
     * @param retryAllowed           whether the operation can be retried
     * @param managerReviewRequired  whether manual manager intervention is needed
     */
    public void saveError(Long offlineOrderImportId, Long companyId, Long branchId,
                          String errorStage, String errorCode, String errorMessage,
                          String fieldPath, String rawValue, OfflineErrorSeverity severity,
                          boolean retryAllowed, boolean managerReviewRequired) {
        errorRepo.insertError(offlineOrderImportId, companyId, branchId,
                errorStage, errorCode, errorMessage, fieldPath, rawValue,
                severity, retryAllowed, managerReviewRequired);
    }

    /**
     * Retrieves all errors associated with a specific sync batch.
     *
     * @param companyId the company ID
     * @param branchId  the branch ID
     * @param batchId   the batch ID
     * @return a list of sync error responses
     */
    public List<SyncErrorResponse> getErrorsByBatchId(Long companyId, Long branchId, Long batchId) {
        return errorRepo.findErrorsByBatchId(companyId, branchId, batchId);
    }

    /**
     * Retrieves a paginated list of errors for a sync batch.
     *
     * @param companyId    the company ID
     * @param branchId     the branch ID
     * @param batchId      the batch ID
     * @param afterErrorId the ID after which to start fetching (cursor)
     * @param pageSize     the maximum number of errors to return
     * @return a paginated sync error list response
     */
    public SyncErrorListResponse getErrorsByBatchId(Long companyId, Long branchId, Long batchId,
                                                    Long afterErrorId, int pageSize) {
        List<SyncErrorItemResponse> fetched = errorRepo.findErrorsByBatchId(
                companyId, branchId, batchId, afterErrorId, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<SyncErrorItemResponse> errors = hasMore ? fetched.subList(0, pageSize) : fetched;
        String nextCursor = null;
        if (hasMore && !errors.isEmpty()) {
            nextCursor = String.valueOf(errors.get(errors.size() - 1).errorId());
        }
        return new SyncErrorListResponse(companyId, branchId, batchId, errors, hasMore, nextCursor);
    }
}
