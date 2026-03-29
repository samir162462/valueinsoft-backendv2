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

import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class InventoryTransactionService {

    private final DbPosInventoryTransaction dbPosInventoryTransaction;

    public InventoryTransactionService(DbPosInventoryTransaction dbPosInventoryTransaction) {
        this.dbPosInventoryTransaction = dbPosInventoryTransaction;
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

        int insertedRows = dbPosInventoryTransaction.insertInventoryTransaction(
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

        if (insertedRows != 1 || supplierRows != 1) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "INVENTORY_TRANSACTION_DEPENDENCY_NOT_FOUND",
                    "Inventory transaction references a missing supplier or target"
            );
        }

        log.info(
                "Created inventory transaction for company {} branch {} product {} supplier {}",
                request.getCompanyId(),
                request.getBranchId(),
                request.getProductId(),
                request.getSupplierId()
        );
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
