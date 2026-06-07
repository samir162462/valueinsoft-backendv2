package com.example.valueinsoftbackend.pricing.dynamic.service;

import com.example.valueinsoftbackend.pricing.dynamic.repository.PricingAuditRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PricingAuditService {

    private final PricingAuditRepository repository;

    public PricingAuditService(PricingAuditRepository repository) {
        this.repository = repository;
    }

    public void log(int companyId, Integer branchId, String eventType, String entityType,
                    String entityId, String actorName, String eventMessage, String payloadJson) {
        try {
            repository.log(companyId, branchId, eventType, entityType, entityId, actorName, eventMessage, payloadJson);
        } catch (Exception exception) {
            log.warn("Dynamic pricing audit write failed: companyId={}, branchId={}, eventType={}, error={}",
                    companyId, branchId, eventType, exception.getMessage());
        }
    }
}
