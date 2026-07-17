package com.example.valueinsoftbackend.Service.openitems;

import com.example.valueinsoftbackend.DatabaseRequests.DbSupplierReceivable;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SupplierReceivableService {
    private final DbSupplierReceivable repository;
    private final ArOpenItemService arOpenItemService;

    public SupplierReceivableService(DbSupplierReceivable repository, ArOpenItemService arOpenItemService) {
        this.repository = repository;
        this.arOpenItemService = arOpenItemService;
    }

    @Transactional
    public long recordPosSupplierSale(int companyId, int branchId, int supplierId, long orderId,
                                      BigDecimal total, BigDecimal paidNow, LocalDateTime orderTime,
                                      String idempotencyKey, String actor) {
        if (supplierId <= 0 || !repository.supplierExists(companyId, branchId, supplierId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RECEIVABLE_SUPPLIER_NOT_FOUND",
                    "The selected supplier was not found in this branch");
        }
        BigDecimal remaining = total.subtract(paidNow);
        if (remaining.signum() <= 0) return 0;
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? "order-" + orderId : idempotencyKey.trim();
        String currency = arOpenItemService.companyCurrency(companyId);
        long openItemId = repository.createOpenItem(companyId, branchId, supplierId, orderId, orderTime,
                currency, total, paidNow, "pos-supplier-order:" + key + ":" + orderId, actor);
        if (paidNow.signum() > 0) {
            repository.createReceiptAndAllocation(companyId, branchId, supplierId, openItemId, orderId, paidNow,
                    currency, "pos-supplier-receipt:" + key + ":" + orderId, actor);
        }
        return openItemId;
    }

    @Transactional
    public long collectPayment(int companyId, int branchId, int supplierId, BigDecimal amount,
                               String currency, String paymentMethod, String idempotencyKey, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_RECEIPT_AMOUNT_INVALID", "Payment amount must be positive");
        }
        if (!repository.supplierExists(companyId, branchId, supplierId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RECEIVABLE_SUPPLIER_NOT_FOUND", "Supplier was not found");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? java.util.UUID.randomUUID().toString() : idempotencyKey.trim();
        Long replay = repository.findReceiptByIdempotency(companyId, key);
        if (replay != null) return replay;
        List<DbSupplierReceivable.OpenBalance> items = repository.findOpenForUpdate(companyId, branchId, supplierId, currency);
        replay = repository.findReceiptByIdempotency(companyId, key);
        if (replay != null) return replay;
        BigDecimal available = items.stream().map(DbSupplierReceivable.OpenBalance::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(available) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SUPPLIER_RECEIPT_OVERPAYMENT", "Payment exceeds the supplier open receivable balance");
        }
        long receiptId = repository.createReceipt(companyId, branchId, supplierId, amount, currency,
                paymentMethod == null || paymentMethod.isBlank() ? "CASH" : paymentMethod.trim(), key, actor);
        BigDecimal left = amount;
        for (DbSupplierReceivable.OpenBalance item : items) {
            if (left.signum() == 0) break;
            BigDecimal allocated = left.min(item.remainingAmount());
            repository.allocateAndUpdate(companyId, branchId, supplierId, receiptId, item, allocated, currency,
                    key + ":" + item.openItemId(), actor);
            left = left.subtract(allocated);
        }
        return receiptId;
    }
}
