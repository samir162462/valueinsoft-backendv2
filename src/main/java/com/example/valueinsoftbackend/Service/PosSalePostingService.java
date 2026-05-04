package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;

@Service
@Slf4j
public class PosSalePostingService {

    private final DbPosOrder dbPosOrder;
    private final DbPosShiftPeriod dbPosShiftPeriod;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public PosSalePostingService(DbPosOrder dbPosOrder,
                                 DbPosShiftPeriod dbPosShiftPeriod,
                                 FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbPosOrder = dbPosOrder;
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    public DbPosOrder.AddOrderResult postSale(int companyId, Order order) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        DbPosOrder.AddOrderResult result = dbPosOrder.addOrder(order, companyId);

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

        enqueueFinancePosSaleAfterCommit(companyId, order, result);
        log.info("Saved order {} for company {} branch {} with {} items",
                result.orderId(), companyId, order.getBranchId(), order.getOrderDetails().size());
        return result;
    }

    private boolean isDirectSale(String type) {
        return "Dirict".equalsIgnoreCase(type)
                || "Direct".equalsIgnoreCase(type)
                || "ظ…ط¨ط§ط´ط±".equals(type);
    }

    private void enqueueFinancePosSaleAfterCommit(int companyId, Order order, DbPosOrder.AddOrderResult result) {
        if (order.getOrderTotal() <= 0) {
            return;
        }

        Runnable enqueue = () -> {
            try {
                financeOperationalPostingService.enqueuePosSale(
                        companyId,
                        order,
                        result.orderId(),
                        result.orderTime(),
                        dbPosOrder.getOrderFinanceCostLines(result.orderId(), order.getBranchId(), companyId));
            } catch (RuntimeException exception) {
                log.warn(
                        "POS order {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                        result.orderId(),
                        companyId,
                        order.getBranchId(),
                        exception.getMessage());
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
