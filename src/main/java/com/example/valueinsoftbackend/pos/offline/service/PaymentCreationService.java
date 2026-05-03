package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Placeholder service for creating payment records from offline orders.
 * TODO: Implement actual payment creation in Phase 2.
 *   - Map OfflinePaymentRequest entries to the existing payment flow
 *   - Support multiple payment methods per order
 *   - Validate payment totals match order total
 */
@Service
@Slf4j
public class PaymentCreationService {

    public void createPaymentsPlaceholder(OfflineOrderRequest order) {
        // TODO: Implement actual payment creation in Phase 2
        log.info("PLACEHOLDER: Would create payments for offlineOrderNo={}",
                order.offlineOrderNo());
    }
}
