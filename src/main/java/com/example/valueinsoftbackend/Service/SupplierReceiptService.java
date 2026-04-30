package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMSupplierReceipt;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Request.SupplierReceiptCreateRequest;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
@Slf4j
public class SupplierReceiptService {

    private final DBMSupplierReceipt supplierReceiptRepository;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public SupplierReceiptService(DBMSupplierReceipt supplierReceiptRepository,
                                  FinanceOperationalPostingService financeOperationalPostingService) {
        this.supplierReceiptRepository = supplierReceiptRepository;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    public List<SupplierReceipt> getSupplierReceipts(int companyId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return supplierReceiptRepository.getSupplierReceipts(companyId, supplierId);
    }

    @Transactional
    public SupplierReceipt addSupplierReceipt(int companyId, SupplierReceiptCreateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (request.getRemainingAmount().signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_RECEIPT_INVALID_REMAINING", "remainingAmount must be zero or greater");
        }

        Timestamp receiptTime = new Timestamp(System.currentTimeMillis());
        SupplierReceipt supplierReceipt = new SupplierReceipt(
                0,
                request.getTransId(),
                request.getAmountPaid(),
                request.getRemainingAmount(),
                receiptTime,
                request.getUserRecived().trim(),
                request.getSupplierId(),
                request.getType().trim(),
                request.getBranchId()
        );

        SupplierReceipt created = supplierReceiptRepository.createSupplierReceipt(companyId, supplierReceipt);
        if (created == null) {
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
        enrichFinanceSupplierReceipt(companyId, created);

        log.info(
                "Recorded supplier receipt for company {} branch {} supplier {} transaction {}",
                companyId,
                request.getBranchId(),
                request.getSupplierId(),
                request.getTransId()
        );
        return created;
    }

    private void enrichFinanceSupplierReceipt(int companyId, SupplierReceipt receipt) {
        if (receipt == null || receipt.getAmountPaid() == null || receipt.getAmountPaid().signum() <= 0) {
            return;
        }

        try {
            FinancePostingRequestItem postingRequest = financeOperationalPostingService.enqueueSupplierPayment(companyId, receipt);
            if (postingRequest != null) {
                receipt.setPostingStatus(postingRequest.getStatus());
                receipt.setPostingRequestId(postingRequest.getPostingRequestId());
                receipt.setJournalId(postingRequest.getJournalEntryId());
                receipt.setPostingFailureReason(postingRequest.getLastError());
            }
        } catch (RuntimeException exception) {
            receipt.setPostingStatus("failed");
            receipt.setPostingFailureReason(exception.getMessage());
            log.warn(
                    "Supplier receipt {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                    receipt.getSrId(),
                    companyId,
                    receipt.getBranchId(),
                    exception.getMessage()
            );
        }
    }
}
