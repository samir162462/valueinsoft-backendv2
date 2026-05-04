package com.example.valueinsoftbackend.pos.offline.service;

import com.example.valueinsoftbackend.pos.offline.config.OfflinePosWorkerProperties;
import com.example.valueinsoftbackend.pos.offline.model.PosSyncBatchModel;
import com.example.valueinsoftbackend.pos.offline.repository.PosSyncBatchRepository;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Background worker that periodically scans for active offline sync batches and orchestrates
 * their processing, validation, and posting.
 * The frequency is controlled via {@code valueinsoft.pos.offline.worker.fixed-delay-ms}.
 */
@Service
@Slf4j
public class OfflinePosWorker {

    private final OfflinePosWorkerProperties properties;
    private final PosSyncBatchRepository batchRepo;
    private final PosOfflineSyncService syncService;

    /**
     * Constructs a new OfflinePosWorker with required configuration and services.
     *
     * @param properties  the worker configuration properties
     * @param batchRepo   the repository for sync batches
     * @param syncService the service orchestrating the sync lifecycle
     */
    public OfflinePosWorker(OfflinePosWorkerProperties properties,
                            PosSyncBatchRepository batchRepo,
                            PosOfflineSyncService syncService) {
        this.properties = properties;
        this.batchRepo = batchRepo;
        this.syncService = syncService;
    }

    /**
     * Scheduled entry point for the background worker.
     * Iterates through configured targets and processes active batches.
     */
    @Scheduled(fixedDelayString = "${valueinsoft.pos.offline.worker.fixed-delay-ms:30000}")
    public void runCycle() {
        if (!properties.isEnabled()) {
            log.debug("Offline POS worker skipped: disabled");
            return;
        }

        List<WorkerTarget> targets = parseTargets(properties.getTargets());
        if (targets.isEmpty()) {
            log.info("Offline POS worker skipped: no companyId:branchId targets configured");
            return;
        }

        log.info("Offline POS worker cycle started: targets={}, processing={}, validation={}, posting={}",
                targets.size(),
                properties.isProcessingEnabled(),
                properties.isValidationEnabled(),
                properties.isPostingEnabled());

        int batchesProcessed = 0;
        for (WorkerTarget target : targets) {
            List<PosSyncBatchModel> batches = findActiveBatches(target);
            for (PosSyncBatchModel batch : batches) {
                processBatch(batch);
                batchesProcessed++;
            }
        }

        log.info("Offline POS worker cycle completed: batchesProcessed={}", batchesProcessed);
    }

    /**
     * Finds active batches for a specific worker target.
     *
     * @param target the company and branch to scan
     * @return a list of active batches
     */
    private List<PosSyncBatchModel> findActiveBatches(WorkerTarget target) {
        try {
            return batchRepo.findActiveBatchesForWorker(
                    target.companyId(),
                    target.branchId(),
                    Math.max(1, properties.getBatchSize()));
        } catch (Exception ex) {
            log.warn("Offline POS worker failed to list batches for company {} branch {}: {}",
                    target.companyId(), target.branchId(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * Processes a single sync batch through its lifecycle stages.
     *
     * @param batch the batch to process
     */
    private void processBatch(PosSyncBatchModel batch) {
        Long companyId = batch.companyId();
        Long branchId = batch.branchId();
        Long batchId = batch.id();
        try {
            int recovered = syncService.recoverStuckImports(
                    companyId, branchId, batchId, Math.max(1, properties.getStuckThresholdMinutes()));
            int processed = 0;
            int validated = 0;
            int posted = 0;

            if (properties.isProcessingEnabled()) {
                processed = syncService.processPendingImports(companyId, branchId, batchId);
            }
            if (properties.isValidationEnabled()) {
                validated = syncService.validateReadyImports(companyId, branchId, batchId);
            }
            if (properties.isPostingEnabled()) {
                posted = syncService.postValidatedImports(companyId, branchId, batchId);
            }

            syncService.recalculateBatchSummary(companyId, branchId, batchId);
            log.info("Offline POS worker batch completed: companyId={}, branchId={}, batchId={}, recovered={}, processed={}, validated={}, posted={}",
                    companyId, branchId, batchId, recovered, processed, validated, posted);
        } catch (Exception ex) {
            log.warn("Offline POS worker batch failed: companyId={}, branchId={}, batchId={}, error={}",
                    companyId, branchId, batchId, ex.getMessage());
        }
    }

    /**
     * Parses the comma-separated target string into {@link WorkerTarget} objects.
     * Format: companyId:branchId,companyId2:branchId2
     *
     * @param rawTargets the raw target string
     * @return a list of parsed targets
     */
    private List<WorkerTarget> parseTargets(String rawTargets) {
        if (rawTargets == null || rawTargets.isBlank()) {
            return List.of();
        }
        String[] tokens = rawTargets.split(",");
        List<WorkerTarget> targets = new ArrayList<>();
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split(":");
            if (parts.length != 2) {
                log.warn("Offline POS worker target ignored because it is not companyId:branchId: {}", trimmed);
                continue;
            }
            try {
                long companyId = Long.parseLong(parts[0].trim());
                long branchId = Long.parseLong(parts[1].trim());
                TenantSqlIdentifiers.requirePositive(companyId, "companyId");
                TenantSqlIdentifiers.requirePositive(branchId, "branchId");
                targets.add(new WorkerTarget(companyId, branchId));
            } catch (Exception ex) {
                log.warn("Offline POS worker target ignored because it is invalid: {}", trimmed);
            }
        }
        return targets;
    }

    /**
     * Value record for a company-branch processing target.
     */
    private record WorkerTarget(Long companyId, Long branchId) {
    }
}
