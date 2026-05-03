package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosProperties;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderRequest;
import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineSyncUploadRequest;
import com.example.valueinsoftbackend.pos.offline.exception.OfflineSyncException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OfflineOrderValidationService {

    private final OfflinePosProperties props;

    public OfflineOrderValidationService(OfflinePosProperties props) {
        this.props = props;
    }

    /**
     * Validates batch-level fields before individual order processing.
     */
    public void validateBatch(OfflineSyncUploadRequest request) {
        if (request.orders() == null || request.orders().isEmpty()) {
            throw new OfflineSyncException("VALIDATION_FAILED", "Batch must contain at least one order");
        }

        if (request.orders().size() > props.getMaxOrdersPerBatch()) {
            throw new OfflineSyncException("BATCH_TOO_LARGE",
                    "Max orders per batch is " + props.getMaxOrdersPerBatch());
        }

        // TODO: Validate that cashierId exists and is active for this branch
        // TODO: Validate that branch is active and belongs to the company
        // TODO: Validate offline window (offlineStartedAt vs. device maxOfflineHours)
    }

    /**
     * Validates a single offline order's structure and business rules.
     */
    public void validateOrder(OfflineOrderRequest order) {
        if (order.offlineOrderNo() == null || order.offlineOrderNo().isBlank()) {
            throw new OfflineSyncException("VALIDATION_FAILED", "offlineOrderNo is required");
        }
        if (order.idempotencyKey() == null || order.idempotencyKey().isBlank()) {
            throw new OfflineSyncException("VALIDATION_FAILED", "idempotencyKey is required");
        }
        if (order.items() == null || order.items().isEmpty()) {
            throw new OfflineSyncException("VALIDATION_FAILED", "Order must contain at least one item");
        }
        if (order.items().size() > props.getMaxItemsPerOrder()) {
            throw new OfflineSyncException("VALIDATION_FAILED",
                    "Max items per order is " + props.getMaxItemsPerOrder());
        }

        // TODO: Validate each item's productId exists in tenant catalog
        // TODO: Validate prices have not changed beyond acceptable threshold
        // TODO: Validate tax calculations are correct
        // TODO: Validate discount rules
        // TODO: Validate payment totals match order total
        // TODO: Validate stock availability for each item
        // TODO: Validate shift is open (if localShiftId provided)
    }
}
