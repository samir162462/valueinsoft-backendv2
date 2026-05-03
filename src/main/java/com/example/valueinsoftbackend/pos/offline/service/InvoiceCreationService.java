package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Placeholder service for creating official invoices from offline orders.
 * TODO: Implement actual invoice creation in Phase 2.
 *   - Map OfflineOrderRequest to the existing Order/Invoice creation flow
 *   - Use the tenant's PosOrder_{branchId} table via DbPosOrder
 *   - Generate official invoice number
 *   - Link to shift if applicable
 *   - Return the official order ID
 */
@Service
@Slf4j
public class InvoiceCreationService {

    public Long createOfficialInvoicePlaceholder(OfflineOrderRequest order) {
        // TODO: Implement actual invoice creation in Phase 2
        log.info("PLACEHOLDER: Would create official invoice for offlineOrderNo={}",
                order.offlineOrderNo());
        return null;
    }
}
