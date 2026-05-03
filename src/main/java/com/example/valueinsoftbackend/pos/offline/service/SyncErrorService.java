package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorItemResponse;
import com.example.valueinsoftbackend.pos.offline.dto.response.SyncErrorListResponse;
import com.example.valueinsoftbackend.pos.offline.enums.OfflineErrorSeverity;
import com.example.valueinsoftbackend.pos.offline.repository.OfflineOrderErrorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class SyncErrorService {

    private final OfflineOrderErrorRepository errorRepo;

    public SyncErrorService(OfflineOrderErrorRepository errorRepo) {
        this.errorRepo = errorRepo;
    }

    public void saveError(Long offlineOrderImportId, Long companyId, Long branchId,
                          String errorStage, String errorCode, String errorMessage,
                          String fieldPath, String rawValue, OfflineErrorSeverity severity,
                          boolean retryAllowed, boolean managerReviewRequired) {
        errorRepo.insertError(offlineOrderImportId, companyId, branchId,
                errorStage, errorCode, errorMessage, fieldPath, rawValue,
                severity, retryAllowed, managerReviewRequired);
    }

    public List<SyncErrorResponse> getErrorsByBatchId(Long companyId, Long branchId, Long batchId) {
        return errorRepo.findErrorsByBatchId(companyId, branchId, batchId);
    }

    public SyncErrorListResponse getErrorsByBatchId(Long companyId, Long branchId, Long batchId,
                                                    Long afterErrorId, int pageSize) {
        List<SyncErrorItemResponse> fetched = errorRepo.findErrorsByBatchId(
                companyId, branchId, batchId, afterErrorId, pageSize);
        boolean hasMore = fetched.size() > pageSize;
        List<SyncErrorItemResponse> errors = hasMore ? fetched.subList(0, pageSize) : fetched;
        String nextCursor = null;
        if (hasMore && !errors.isEmpty()) {
            nextCursor = String.valueOf(errors.get(errors.size() - 1).errorId());
        }
        return new SyncErrorListResponse(companyId, branchId, batchId, errors, hasMore, nextCursor);
    }
}
