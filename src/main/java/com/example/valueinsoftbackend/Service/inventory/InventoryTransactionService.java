package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.Service.SerializedInventoryService;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosInventoryTransaction;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbInventoryProductTrackingRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.Model.Inventory.ProductTrackingMetadata;
import com.example.valueinsoftbackend.Model.Inventory.TrackingType;
import com.example.valueinsoftbackend.Model.Request.CreateInventoryTransactionRequest;
import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitStockInRequest;
import com.example.valueinsoftbackend.Model.Request.InventoryTransactionQueryRequest;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final DbInventoryProductTrackingRepository productTrackingRepository;
    private final SerializedInventoryService serializedInventoryService;

    public InventoryTransactionService(DbPosInventoryTransaction dbPosInventoryTransaction,
                                       FinanceOperationalPostingService financeOperationalPostingService) {
        this(dbPosInventoryTransaction, financeOperationalPostingService, null, null);
    }

    @Autowired
    public InventoryTransactionService(DbPosInventoryTransaction dbPosInventoryTransaction,
                                       FinanceOperationalPostingService financeOperationalPostingService,
                                       DbInventoryProductTrackingRepository productTrackingRepository,
                                       SerializedInventoryService serializedInventoryService) {
        this.dbPosInventoryTransaction = dbPosInventoryTransaction;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.productTrackingRepository = productTrackingRepository;
        this.serializedInventoryService = serializedInventoryService;
    }

    @Transactional
    public void addTransaction(CreateInventoryTransactionRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        TenantSqlIdentifiers.requirePositive(request.getProductId(), "productId");
        TenantSqlIdentifiers.requirePositive(request.getSupplierId(), "supplierId");
        TrackingType trackingType = resolveProductTrackingType(request.getCompanyId(), request.getProductId());
        validateSerializedInventoryTransactionRequest(request, trackingType);

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

        if (trackingType.isSerialized() && request.getNumItems() > 0) {
            SerializedUnitStockInRequest stockInRequest = new SerializedUnitStockInRequest(
                    request.getCompanyId(),
                    request.getBranchId(),
                    request.getProductId(),
                    trackingType,
                    (long) request.getSupplierId(),
                    "INVENTORY_TRANSACTION",
                    String.valueOf(insertedTransaction.transactionId()),
                    null,
                    request.getUserName(),
                    request.getIdempotencyKey(),
                    request.getSerializedUnits()
            );
            serializedInventoryService.stockInSerializedUnits(stockInRequest);
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

    public List<Long> addSerializedStockIn(SerializedUnitStockInRequest request) {
        return serializedInventoryService.stockInSerializedUnits(request);
    }

    private TrackingType resolveProductTrackingType(int companyId, int productId) {
        if (productTrackingRepository == null) {
            return TrackingType.QUANTITY;
        }
        return productTrackingRepository.findTrackingMetadata(companyId, productId)
                .map(ProductTrackingMetadata::getTrackingType)
                .map(TrackingType::defaultIfNull)
                .orElse(TrackingType.QUANTITY);
    }

    private void validateSerializedInventoryTransactionRequest(CreateInventoryTransactionRequest request, TrackingType trackingType) {
        if (!trackingType.isSerialized()) {
            return;
        }
        if (serializedInventoryService == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZED_INVENTORY_SERVICE_UNAVAILABLE", "Serialized inventory service is unavailable");
        }
        if (request.getNumItems() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SERIALIZED_INVENTORY_TRANSACTION_UNSUPPORTED",
                    "Serialized stock changes must receive positive stock-in units in this flow"
            );
        }
        int unitCount = request.getSerializedUnits() == null ? 0 : request.getSerializedUnits().size();
        if (unitCount != request.getNumItems()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SERIALIZED_STOCK_IN_UNIT_COUNT_MISMATCH",
                    "Serialized stock-in unit count must equal numItems"
            );
        }
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
