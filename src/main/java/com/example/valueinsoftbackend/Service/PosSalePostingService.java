package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRecordedEarn;
import com.example.valueinsoftbackend.loyalty.service.LoyaltyService;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    public PosSalePostingService(DbPosOrder dbPosOrder,
            DbPosShiftPeriod dbPosShiftPeriod,
            FinanceOperationalPostingService financeOperationalPostingService,
            LoyaltyService loyaltyService) {
        this.dbPosOrder = dbPosOrder;
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.loyaltyService = loyaltyService;
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

        if (shiftId != null && order.getOrderTotal() > 0 && isDirectSale(order.getOrderType())) {
            dbPosShiftPeriod.insertCashMovement(
                    companyId, shiftId, order.getBranchId(),
                    "CASH_SALE", BigDecimal.valueOf(order.getOrderTotal()),
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
        return "Dirict".equalsIgnoreCase(type)
                || "Direct".equalsIgnoreCase(type)
                || "مباشر".equals(type);
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

