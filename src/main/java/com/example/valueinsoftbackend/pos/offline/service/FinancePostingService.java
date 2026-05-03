package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.dto.request.OfflineOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Placeholder service for posting finance journal entries from offline orders.
 * TODO: Implement actual finance posting in Phase 2.
 *   - Use existing FinanceOperationalPostingService / FinancePosPostingAdapter
 *   - Create journal entries for revenue, COGS, tax, etc.
 *   - Handle posting failures gracefully
 */
@Service
@Slf4j
public class FinancePostingService {

    public void postFinanceJournalPlaceholder(OfflineOrderRequest order) {
        // TODO: Implement actual finance journal posting in Phase 2
        log.info("PLACEHOLDER: Would post finance journal for offlineOrderNo={}",
                order.offlineOrderNo());
    }
}
