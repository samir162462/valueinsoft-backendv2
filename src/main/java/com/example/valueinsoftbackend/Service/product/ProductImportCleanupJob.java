package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryImport.ProductImportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(name = "inventory.import.cleanup.enabled", havingValue = "true")
public class ProductImportCleanupJob {

    private final ProductImportRepository importRepository;
    private final int retentionDays;
    private final int maxBatchesPerTenant;

    public ProductImportCleanupJob(ProductImportRepository importRepository,
                                   @Value("${inventory.import.cleanup.retention-days:90}") int retentionDays,
                                   @Value("${inventory.import.cleanup.max-batches-per-tenant:500}") int maxBatchesPerTenant) {
        this.importRepository = importRepository;
        this.retentionDays = retentionDays;
        this.maxBatchesPerTenant = maxBatchesPerTenant;
    }

    @Scheduled(cron = "${inventory.import.cleanup.cron:0 25 3 * * *}")
    public void cleanupExpiredProductImportBatches() {
        int deletedBatches = importRepository.cleanupExpiredBatches(retentionDays, maxBatchesPerTenant);
        if (deletedBatches > 0) {
            log.info("Product import cleanup deleted {} expired batches older than {} days", deletedBatches, retentionDays);
        }
    }
}
