package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Placeholder service for posting inventory movements from offline orders.
 * TODO: Implement actual inventory posting in Phase 2.
 *   - Deduct stock from inventory_branch_stock_balance
 *   - Create inventory_stock_ledger entries
 *   - Create InventoryTransactions_{branchId} records
 *   - Handle stock unavailability gracefully
 */
@Service
@Slf4j
public class InventoryMovementService {

    public void postInventoryMovementPlaceholder(OfflineOrderRequest order) {
        // TODO: Implement actual inventory posting in Phase 2
        log.info("PLACEHOLDER: Would post inventory movement for offlineOrderNo={}",
                order.offlineOrderNo());
    }
}
