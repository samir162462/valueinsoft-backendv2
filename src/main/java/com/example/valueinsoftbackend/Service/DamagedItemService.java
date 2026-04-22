package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosDamagedList;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Request.CreateDamagedItemRequest;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class DamagedItemService {

    private final DbPosDamagedList dbPosDamagedList;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public DamagedItemService(DbPosDamagedList dbPosDamagedList,
                              FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbPosDamagedList = dbPosDamagedList;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    public List<DamagedItem> getDamagedItems(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        return dbPosDamagedList.getDamagedList(companyId, branchId);
    }

    @Transactional
    public String addDamagedItem(int companyId, int branchId, CreateDamagedItemRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        if (request.getBranchId() != branchId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BRANCH_ID_MISMATCH", "branchId in the body does not match the path");
        }

        Integer availableQuantity = dbPosDamagedList.getProductQuantity(companyId, branchId, request.getProductId());
        if (availableQuantity == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
        }
        if (availableQuantity < request.getQuantity()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_PRODUCT_QUANTITY",
                    "Damaged quantity exceeds available product quantity"
            );
        }

        Timestamp time = RequestTimestampParser.parse(request.getTime(), "time");
        DamagedItem damagedItem = new DamagedItem(
                request.getDId(),
                request.getProductId(),
                request.getProductName().trim(),
                time,
                request.getReason().trim(),
                request.getDamagedBy().trim(),
                request.getCashierUser().trim(),
                request.getAmountTP(),
                request.isPaid(),
                request.getBranchId(),
                request.getQuantity()
        );

        DbPosDamagedList.AddDamagedItemResult insertedItem = dbPosDamagedList.insertDamagedItem(companyId, damagedItem);
        int updatedRows = dbPosDamagedList.decrementProductQuantity(companyId, branchId, request.getProductId(), request.getQuantity());
        int inventoryRows = dbPosDamagedList.insertDamagedInventoryTransaction(companyId, branchId, damagedItem);
        DbPosDamagedList.AddDamagedLedgerResult modernLedger = dbPosDamagedList.insertDamagedLedgerEntry(companyId, branchId, damagedItem);

        if (insertedItem.damagedItemId() <= 0 || updatedRows != 1 || inventoryRows != 1 || modernLedger.stockLedgerId() <= 0) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "DAMAGED_ITEM_WRITE_FAILED",
                    "Damaged item side effects were not fully persisted"
            );
        }

        enqueueFinanceDamagedItemAfterCommit(
                companyId,
                branchId,
                damagedItem,
                insertedItem.damagedItemId(),
                modernLedger.stockLedgerId()
        );

        log.info("Created damaged-item row for company {} branch {} product {}", companyId, branchId, request.getProductId());
        return "the DamagedItem added! ok 200";
    }

    private void enqueueFinanceDamagedItemAfterCommit(int companyId,
                                                      int branchId,
                                                      DamagedItem damagedItem,
                                                      int damagedItemId,
                                                      Long inventoryMovementId) {
        if (damagedItem.getAmountTP() <= 0 || damagedItem.getQuantity() <= 0) {
            return;
        }

        Runnable enqueue = () -> {
            try {
                financeOperationalPostingService.enqueueDamagedItem(
                        companyId,
                        branchId,
                        damagedItem,
                        damagedItemId,
                        inventoryMovementId
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Damaged item {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                        damagedItemId,
                        companyId,
                        branchId,
                        exception.getMessage()
                );
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                enqueue.run();
            }
        });
    }

    @Transactional
    public boolean deleteDamagedItem(int companyId, int branchId, int damagedId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(branchId, "branchId");
        TenantSqlIdentifiers.requirePositive(damagedId, "DId");
        boolean deleted = dbPosDamagedList.deleteDamagedItem(companyId, branchId, damagedId);
        log.info("Deleted damaged-item {} for company {} branch {}: {}", damagedId, companyId, branchId, deleted);
        return deleted;
    }
}
