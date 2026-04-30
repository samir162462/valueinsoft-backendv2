package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosInventoryTransaction;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Request.CreateInventoryTransactionRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryTransactionQueryRequest;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class InventoryTransactionService {

    private final DbPosInventoryTransaction dbPosInventoryTransaction;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public InventoryTransactionService(DbPosInventoryTransaction dbPosInventoryTransaction,
                                       FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbPosInventoryTransaction = dbPosInventoryTransaction;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    @Transactional
    public void addTransaction(CreateInventoryTransactionRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.getProductId(), "productId");
        TenantSqlIdentifiers.requirePositive(request.getSupplierId(), "supplierId");

        Timestamp time = RequestTimestampParser.parse(request.getTime(), "time");
        InventoryTransaction inventoryTransaction = new InventoryTransaction(
                0,
                request.getProductId(),
                request.getUserName().trim(),
                request.getSupplierId(),
                request.getTransactionType().trim(),
                request.getNumItems(),
                request.getTransTotal(),
                request.getPayType().trim(),
                time,
                request.getRemainingAmount()
        );

        DbPosInventoryTransaction.AddInventoryTransactionResult insertedTransaction = dbPosInventoryTransaction.insertInventoryTransaction(
                inventoryTransaction,
                request.getBranchId(),
                request.getCompanyId()
        );
        int supplierRows = dbPosInventoryTransaction.updateSupplierTotals(
                request.getCompanyId(),
                request.getBranchId(),
                request.getSupplierId(),
                request.getTransTotal(),
                request.getRemainingAmount()
        );
        dbPosInventoryTransaction.syncLatestLedgerMetadata(
                request.getCompanyId(),
                request.getBranchId(),
                inventoryTransaction
        );
        Long inventoryMovementId = request.getNumItems() > 0
                ? dbPosInventoryTransaction.findLatestPurchaseInventoryMovementId(
                request.getCompanyId(),
                request.getBranchId(),
                inventoryTransaction)
                : dbPosInventoryTransaction.findLatestAdjustmentInventoryMovementId(
                request.getCompanyId(),
                request.getBranchId(),
                inventoryTransaction);

        if (insertedTransaction.transactionId() <= 0 || supplierRows != 1) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "INVENTORY_TRANSACTION_DEPENDENCY_NOT_FOUND",
                    "Inventory transaction references a missing supplier or target"
            );
        }

        if (request.getNumItems() > 0) {
            enqueueFinancePurchaseAfterCommit(
                    request.getCompanyId(),
                    request.getBranchId(),
                    inventoryTransaction,
                    insertedTransaction.transactionId(),
                    inventoryMovementId
            );
        } else if (request.getNumItems() < 0) {
            if (isExplicitPurchaseReturn(inventoryTransaction)) {
                enqueueFinancePurchaseReturnAfterCommit(
                        request.getCompanyId(),
                        request.getBranchId(),
                        inventoryTransaction,
                        insertedTransaction.transactionId(),
                        inventoryMovementId
                );
            } else {
                enqueueFinanceInventoryAdjustmentAfterCommit(
                        request.getCompanyId(),
                        request.getBranchId(),
                        inventoryTransaction,
                        insertedTransaction.transactionId(),
                        inventoryMovementId
                );
            }
        }

        log.info(
                "Created inventory transaction for company {} branch {} product {} supplier {}",
                request.getCompanyId(),
                request.getBranchId(),
                request.getProductId(),
                request.getSupplierId()
        );
    }

    private void enqueueFinancePurchaseAfterCommit(int companyId,
                                                   int branchId,
                                                   InventoryTransaction inventoryTransaction,
                                                   int inventoryTransactionId,
                                                   Long inventoryMovementId) {
        if (inventoryTransaction.getTransTotal() <= 0 || inventoryTransaction.getNumItems() <= 0) {
            return;
        }

        Runnable enqueue = () -> {
            try {
                financeOperationalPostingService.enqueuePurchaseInventoryTransaction(
                        companyId,
                        branchId,
                        inventoryTransaction,
                        inventoryTransactionId,
                        inventoryMovementId
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Inventory transaction {} saved for company {} branch {}, but finance purchase posting request was not enqueued: {}",
                        inventoryTransactionId,
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

    private void enqueueFinanceInventoryAdjustmentAfterCommit(int companyId,
                                                              int branchId,
                                                              InventoryTransaction inventoryTransaction,
                                                              int inventoryTransactionId,
                                                              Long inventoryMovementId) {
        if (inventoryTransaction.getTransTotal() == 0 || inventoryTransaction.getNumItems() == 0) {
            return;
        }

        Runnable enqueue = () -> {
            try {
                financeOperationalPostingService.enqueueInventoryAdjustmentTransaction(
                        companyId,
                        branchId,
                        inventoryTransaction,
                        inventoryTransactionId,
                        inventoryMovementId
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Inventory adjustment transaction {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                        inventoryTransactionId,
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

    private void enqueueFinancePurchaseReturnAfterCommit(int companyId,
                                                         int branchId,
                                                         InventoryTransaction inventoryTransaction,
                                                         int inventoryTransactionId,
                                                         Long inventoryMovementId) {
        if (inventoryTransaction.getTransTotal() == 0 || inventoryTransaction.getNumItems() >= 0) {
            return;
        }

        Runnable enqueue = () -> {
            try {
                financeOperationalPostingService.enqueuePurchaseReturnInventoryTransaction(
                        companyId,
                        branchId,
                        inventoryTransaction,
                        inventoryTransactionId,
                        inventoryMovementId
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Purchase return transaction {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                        inventoryTransactionId,
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

    private boolean isExplicitPurchaseReturn(InventoryTransaction inventoryTransaction) {
        if (inventoryTransaction == null || inventoryTransaction.getNumItems() >= 0) {
            return false;
        }

        String transactionType = inventoryTransaction.getTransactionType();
        if (transactionType == null || transactionType.isBlank()) {
            return false;
        }

        String normalized = transactionType
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized.contains("purchase return")
                || normalized.contains("supplier return")
                || normalized.contains("vendor return")
                || normalized.contains("bouncebackinv")
                || normalized.contains("return supplier")
                || normalized.contains("return vendor")
                || normalized.contains("return to supplier")
                || normalized.contains("return to vendor");
    }

    public List<InventoryTransaction> getTransactions(InventoryTransactionQueryRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        return dbPosInventoryTransaction.getInventoryTrans(
                request.getCompanyId(),
                request.getBranchId(),
                request.getStartTime().trim(),
                request.getEndTime().trim()
        );
    }
}
