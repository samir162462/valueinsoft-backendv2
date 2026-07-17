package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.finance.PaymentTypeClassifier;
import com.example.valueinsoftbackend.Service.openitems.ArOpenItemService;
import com.example.valueinsoftbackend.Service.client.ClientReceiptService;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRecordedEarn;
import com.example.valueinsoftbackend.loyalty.service.LoyaltyService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.example.valueinsoftbackend.Model.Finance.FinancePostingRequestItem;

@Service
@Slf4j
public class PosSalePostingService {

    private final DbPosOrder dbPosOrder;
    private final DbPosShiftPeriod dbPosShiftPeriod;
    private final FinanceOperationalPostingService financeOperationalPostingService;
    private final LoyaltyService loyaltyService;
    private ArOpenItemService arOpenItemService;
    private ClientReceiptService clientReceiptService;
    private com.example.valueinsoftbackend.Service.openitems.SupplierReceivableService supplierReceivableService;
    private com.example.valueinsoftbackend.Service.openitems.CreditControlService creditControlService;

    public PosSalePostingService(DbPosOrder dbPosOrder,
            DbPosShiftPeriod dbPosShiftPeriod,
            FinanceOperationalPostingService financeOperationalPostingService,
            LoyaltyService loyaltyService) {
        this.dbPosOrder = dbPosOrder;
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.loyaltyService = loyaltyService;
    }

    @Autowired
    void setArOpenItemService(ArOpenItemService arOpenItemService) {
        this.arOpenItemService = arOpenItemService;
    }

    @Autowired
    void setClientReceiptService(ClientReceiptService clientReceiptService) {
        this.clientReceiptService = clientReceiptService;
    }

    @Autowired
    void setSupplierReceivableService(com.example.valueinsoftbackend.Service.openitems.SupplierReceivableService service) {
        this.supplierReceivableService = service;
    }

    @Autowired(required = false)
    void setCreditControlService(com.example.valueinsoftbackend.Service.openitems.CreditControlService creditControlService) {
        this.creditControlService = creditControlService;
    }

    public com.example.valueinsoftbackend.Model.Response.CreateOrderResult postSale(int companyId, Order order) {
        return postSale(companyId, order, null, null);
    }

    public com.example.valueinsoftbackend.Model.Response.CreateOrderResult postSale(
            int companyId,
            Order order,
            BiConsumer<com.example.valueinsoftbackend.Model.Response.CreateOrderResult, Optional<FinancePostingRequestItem>> onSuccess,
            BiConsumer<com.example.valueinsoftbackend.Model.Response.CreateOrderResult, RuntimeException> onFailure) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        com.example.valueinsoftbackend.Model.Response.CreateOrderResult result = dbPosOrder.addOrder(order, companyId);
        if (result.idempotencyHit()) {
            log.info("Idempotency hit detected in PosSalePostingService, skipping downstream posting for order {} receipt {}", result.orderId(), result.receiptNumber());
            if (onSuccess != null) {
                onSuccess.accept(result, Optional.empty());
            }
            return result;
        }

        boolean receivableSale = PaymentTypeClassifier.classify(order.getOrderType()).category()
                == PaymentTypeClassifier.Category.RECEIVABLE;
        BigDecimal orderTotal = BigDecimal.valueOf(order.getOrderTotal());
        BigDecimal paidNow = normalizedPaidNow(order, orderTotal);
        if (!receivableSale && paidNow.signum() > 0 && paidNow.compareTo(orderTotal) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POS_PARTIAL_PAYMENT_REQUIRES_CREDIT",
                    "A partial POS payment requires a client receivable balance");
        }

        if (receivableSale) {
            boolean supplierBuyer = "SUPPLIER".equalsIgnoreCase(order.getReceivablePartyType());
            if (!supplierBuyer && order.getClientId() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CREDIT_SALE_CLIENT_REQUIRED",
                        "Credit sales require a client");
            }
            if (supplierBuyer && (order.getReceivableSupplierId() == null || order.getReceivableSupplierId() <= 0)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CREDIT_SALE_SUPPLIER_REQUIRED",
                        "Supplier credit sales require a supplier");
            }
            BigDecimal remaining = orderTotal.subtract(paidNow);
            if (remaining.signum() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "POS_RECEIVABLE_BALANCE_REQUIRED",
                        "A receivable sale must leave a positive balance to collect later");
            }
            // Stage 5.1 credit control: runs in the SAME transaction as the order insert and
            // the open-item insert. Locks the client row, so concurrent credit sales against
            // one client serialize on the limit. Denial rolls back the already-inserted order.
            if (!supplierBuyer && creditControlService != null) {
                var creditCheck = creditControlService.checkCreditSale(
                        companyId, order.getBranchId(), order.getClientId(),
                        remaining);
                if (!creditCheck.allowed()) {
                    throw new ApiException(HttpStatus.CONFLICT, creditCheck.reasonCode(),
                            creditCheck.message() == null ? "Credit sale denied" : creditCheck.message());
                }
                if (creditCheck.warning()) {
                    log.warn("Credit sale allowed with limit warning for order {} (client {}): {}",
                            result.orderId(), order.getClientId(), creditCheck.message());
                }
            }
            if (supplierBuyer) {
                supplierReceivableService.recordPosSupplierSale(companyId, order.getBranchId(),
                        order.getReceivableSupplierId(), result.orderId(), orderTotal, paidNow,
                        result.orderTime().toLocalDateTime(), order.getIdempotencyKey(), order.getSalesUser());
            } else if (arOpenItemService != null) {
                long openItemId = arOpenItemService.createPosOrderOpenItem(
                        companyId, order.getBranchId(), order.getClientId(), result.orderId(),
                        orderTotal, result.orderTime().toLocalDateTime(),
                        order.getIdempotencyKey(), order.getSalesUser());
                if (paidNow.signum() > 0) {
                    if (clientReceiptService == null) {
                        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "POS_CHECKOUT_RECEIPT_UNAVAILABLE",
                                "POS checkout payment service is unavailable");
                    }
                    clientReceiptService.recordPosCheckoutPayment(companyId, order.getBranchId(), order.getClientId(),
                            openItemId, paidNow, order.getIdempotencyKey(), order.getSalesUser());
                }
            }
        }

        confirmLoyaltyRedemption(companyId, order, result);
        recordLoyaltyEarn(companyId, order, result);

        Integer shiftId = result.shiftId();
        if (shiftId == null) {
            com.example.valueinsoftbackend.Model.Shift.Shift activeShift = dbPosShiftPeriod.getActiveShift(
                    companyId, order.getBranchId());
            if (activeShift != null) {
                shiftId = activeShift.getShiftId();
            }
        }

        BigDecimal cashReceived = isDirectSale(order.getOrderType())
                ? orderTotal
                : receivableSale ? paidNow : BigDecimal.ZERO;
        if (shiftId != null && cashReceived.signum() > 0) {
            dbPosShiftPeriod.insertCashMovement(
                    companyId, shiftId, order.getBranchId(), "CASH_SALE", cashReceived,
                    order.getSalesUser(), "Sale #" + result.orderId(),
                    order.getClientId() > 0 ? order.getClientId() : null,
                    null, "ORDER", String.valueOf(result.orderId()));
        }

        enqueueFinancePosSaleAfterCommit(companyId, order, result, onSuccess, onFailure);
        log.info("Saved order {} for company {} branch {} with {} items",
                result.orderId(), companyId, order.getBranchId(), order.getOrderDetails().size());
        return result;
    }

    private void confirmLoyaltyRedemption(int companyId, Order order, com.example.valueinsoftbackend.Model.Response.CreateOrderResult result) {
        if (loyaltyService == null || order.getLoyaltyRedemptionId() == null || order.getLoyaltyRedemptionId() <= 0) {
            return;
        }
        loyaltyService.confirmOrderRedemption(companyId, order, result);
    }

    private void recordLoyaltyEarn(int companyId, Order order, com.example.valueinsoftbackend.Model.Response.CreateOrderResult result) {
        if (loyaltyService == null) {
            return;
        }
        LoyaltyRecordedEarn earned = loyaltyService.recordOrderEarn(companyId, order, result);
        if (earned.inserted() && earned.pointsEarned() > 0) {
            log.info("Awarded {} loyalty points for company {} branch {} order {} client {}",
                    earned.pointsEarned(), companyId, order.getBranchId(), result.orderId(), order.getClientId());
        }
    }

    private boolean isDirectSale(String type) {
        return PaymentTypeClassifier.classify(type).category() == PaymentTypeClassifier.Category.CASH;
    }

    private BigDecimal normalizedPaidNow(Order order, BigDecimal orderTotal) {
        BigDecimal paidNow = order.getPaidNowAmount() == null ? BigDecimal.ZERO : order.getPaidNowAmount();
        if (paidNow.signum() < 0 || paidNow.compareTo(orderTotal) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "POS_PAID_NOW_AMOUNT_INVALID",
                    "Paid now amount must be between zero and the order total");
        }
        return paidNow;
    }

    private void enqueueFinancePosSaleAfterCommit(
            int companyId,
            Order order,
            com.example.valueinsoftbackend.Model.Response.CreateOrderResult result,
            BiConsumer<com.example.valueinsoftbackend.Model.Response.CreateOrderResult, Optional<FinancePostingRequestItem>> onSuccess,
            BiConsumer<com.example.valueinsoftbackend.Model.Response.CreateOrderResult, RuntimeException> onFailure) {
        if (order.getOrderTotal() <= 0) {
            if (onSuccess != null) {
                onSuccess.accept(result, Optional.empty());
            }
            return;
        }

        Runnable enqueue = () -> {
            try {
                FinancePostingRequestItem request = financeOperationalPostingService.enqueuePosSaleAndReturnRequest(
                        companyId,
                        order,
                        result.orderId(),
                        result.orderTime(),
                        dbPosOrder.getOrderFinanceCostLines(result.orderId(), order.getBranchId(), companyId));
                if (onSuccess != null) {
                    onSuccess.accept(result, Optional.ofNullable(request));
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "POS order {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                        result.orderId(),
                        companyId,
                        order.getBranchId(),
                        exception.getMessage());
                if (onFailure != null) {
                    onFailure.accept(result, exception);
                }
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
}
