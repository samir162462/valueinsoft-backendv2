package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryImport.ProductImportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class ProductImportAuditService {

    private final ProductImportRepository importRepository;

    public ProductImportAuditService(ProductImportRepository importRepository) {
        this.importRepository = importRepository;
    }

    public void logEvent(int companyId, int branchId, Long batchId, String eventType,
                         String eventMessage, String actorName, Map<String, Object> payload) {
        try {
            importRepository.insertAuditLog(
                    companyId,
                    branchId,
                    batchId,
                    eventType,
                    eventMessage,
                    actorName,
                    payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            log.warn("Product import audit write failed: companyId={}, branchId={}, batchId={}, eventType={}, error={}",
                    companyId,
                    branchId,
                    batchId,
                    eventType,
                    ex.getMessage());
        }
    }
}
