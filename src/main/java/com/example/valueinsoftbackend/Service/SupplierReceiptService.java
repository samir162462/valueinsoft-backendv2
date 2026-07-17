package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.openitems.ApOpenItemService;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbMoney.DBMSupplierReceipt;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;
import com.example.valueinsoftbackend.Model.Request.SupplierReceiptCreateRequest;
import com.example.valueinsoftbackend.Model.Sales.SupplierReceipt;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class SupplierReceiptService {

    private final DBMSupplierReceipt supplierReceiptRepository;
    private final FinanceOperationalPostingService financeOperationalPostingService;
    private final ApOpenItemService apOpenItemService;
    private final boolean legacyWritesEnabled;

    public SupplierReceiptService(DBMSupplierReceipt supplierReceiptRepository,
                                  FinanceOperationalPostingService financeOperationalPostingService,
                                  ApOpenItemService apOpenItemService,
                                  @Value("${finance.openitems.legacy-writes.enabled:true}") boolean legacyWritesEnabled) {
        this.supplierReceiptRepository = supplierReceiptRepository;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.apOpenItemService = apOpenItemService;
        this.legacyWritesEnabled = legacyWritesEnabled;
    }

    public List<SupplierReceipt> getSupplierReceipts(int companyId, int supplierId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return supplierReceiptRepository.getSupplierReceipts(companyId, supplierId);
    }

    @Transactional
    public SupplierReceipt addSupplierReceipt(int companyId, SupplierReceiptCreateRequest request) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        if (request.getRemainingAmount() != null) {
            log.info("Ignoring client-supplied remainingAmount for company {} branch {} supplier {}; server computes it",
                    companyId, request.getBranchId(), request.getSupplierId());
        }

        BigDecimal beforeRemaining = apOpenItemService.purchaseRemaining(
                companyId, request.getBranchId(), request.getSupplierId(), request.getTransId());
        BigDecimal serverRemaining = beforeRemaining.subtract(request.getAmountPaid()).max(BigDecimal.ZERO);

        Timestamp receiptTime = new Timestamp(System.currentTimeMillis());
        SupplierReceipt supplierReceipt = new SupplierReceipt(
                0,
                request.getTransId(),
                request.getAmountPaid(),
                serverRemaining,
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


        String currency = request.getCurrencyCode() == null || request.getCurrencyCode().isBlank()
                ? apOpenItemService.companyCurrency(companyId)
                : request.getCurrencyCode().trim().toUpperCase();
        OpenItemsWriteModels.AllocationResult allocationResult = apOpenItemService.allocateReceipt(
                companyId, request.getBranchId(), request.getSupplierId(), created.getSrId(),
                new OpenItemsWriteModels.AllocationCommand(currency, request.getIdempotencyKey(), request.getAllocations()),
                request.getUserRecived().trim());
        serverRemaining = apOpenItemService.purchaseRemaining(
                companyId, request.getBranchId(), request.getSupplierId(), request.getTransId());

        if (legacyWritesEnabled) {
            int inventoryRows = supplierReceiptRepository.updateInventoryRemainingAmount(
                    companyId, request.getBranchId(), request.getTransId(), serverRemaining);
            int supplierRows = supplierReceiptRepository.decrementSupplierRemaining(
                    companyId, request.getBranchId(), request.getSupplierId(), allocationResult.allocatedAmount());
            supplierReceiptRepository.verifyDependentRows(inventoryRows, supplierRows);
        }
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
