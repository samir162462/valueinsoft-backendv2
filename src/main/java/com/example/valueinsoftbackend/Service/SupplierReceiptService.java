package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMSupplierReceipt;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Request.SupplierReceiptCreateRequest;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class SupplierReceiptService {

    private final DBMSupplierReceipt supplierReceiptRepository;

    public SupplierReceiptService(DBMSupplierReceipt supplierReceiptRepository) {
        this.supplierReceiptRepository = supplierReceiptRepository;
    }

    public List<SupplierReceipt> getSupplierReceipts(int companyId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return supplierReceiptRepository.getSupplierReceipts(companyId, supplierId);
    }

    @Transactional
    public String addSupplierReceipt(int companyId, SupplierReceiptCreateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (request.getRemainingAmount().signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_RECEIPT_INVALID_REMAINING", "remainingAmount must be zero or greater");
        }

        SupplierReceipt supplierReceipt = new SupplierReceipt(
                0,
                request.getTransId(),
                request.getAmountPaid(),
                request.getRemainingAmount(),
                null,
                request.getUserRecived().trim(),
                request.getSupplierId(),
                request.getType().trim(),
                request.getBranchId()
        );

        int receiptRows = supplierReceiptRepository.insertSupplierReceipt(companyId, supplierReceipt);
        if (receiptRows != 1) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SUPPLIER_RECEIPT_INSERT_FAILED", "the ReceiptUser not added -> error in server!");
        }

        int inventoryRows = supplierReceiptRepository.updateInventoryRemainingAmount(
                companyId,
                request.getBranchId(),
                request.getTransId(),
                request.getRemainingAmount()
        );
        int supplierRows = supplierReceiptRepository.decrementSupplierRemaining(
                companyId,
                request.getBranchId(),
                request.getSupplierId(),
                request.getAmountPaid()
        );
        supplierReceiptRepository.verifyDependentRows(inventoryRows, supplierRows);

        log.info(
                "Recorded supplier receipt for company {} branch {} supplier {} transaction {}",
                companyId,
                request.getBranchId(),
                request.getSupplierId(),
                request.getTransId()
        );
        return "the Client Receipt Added Successfully : " + request.getSupplierId();
    }
}
