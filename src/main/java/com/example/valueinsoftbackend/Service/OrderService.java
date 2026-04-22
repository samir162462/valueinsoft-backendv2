package com.example.valueinsoftbackend.Service;

import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Request.BounceBackOrderRequest;
import com.example.valueinsoftbackend.Model.Request.CreateOrderRequest;
import com.example.valueinsoftbackend.Model.Request.OrderItemRequest;
import com.example.valueinsoftbackend.Model.Request.OrderPeriodRequest;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OrderService {

    private final DbPosOrder dbPosOrder;
    private final com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod dbPosShiftPeriod;
    private final FinanceOperationalPostingService financeOperationalPostingService;

    public OrderService(DbPosOrder dbPosOrder,
            com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod dbPosShiftPeriod,
            FinanceOperationalPostingService financeOperationalPostingService) {
        this.dbPosOrder = dbPosOrder;
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.financeOperationalPostingService = financeOperationalPostingService;
    }

    @Transactional
    public int createOrder(CreateOrderRequest request, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        Order order = toOrder(request);
        DbPosOrder.AddOrderResult result = dbPosOrder.addOrder(order, companyId);

        // Resolve shiftId: Prefer the result from addOrder, fallback to searching for
        // active shift
        Integer shiftId = result.shiftId();
        if (shiftId == null) {
            com.example.valueinsoftbackend.Model.Shift.Shift activeShift = dbPosShiftPeriod.getActiveShift(companyId,
                    order.getBranchId());
            if (activeShift != null) {
                shiftId = activeShift.getShiftId();
            }
        }

        // Record as cash movement if shift is active
        // We handle 'Dirict' (standard POS), 'Direct', and Arabic 'مباشر' or just any
        // non-empty type
        if (shiftId != null && order.getOrderTotal() > 0) {
            String type = order.getOrderType();
            boolean isStandardSale = "Dirict".equalsIgnoreCase(type) || "Direct".equalsIgnoreCase(type)
                    || "مباشر".equals(type);

            if (isStandardSale) {
                dbPosShiftPeriod.insertCashMovement(
                        companyId, shiftId, order.getBranchId(),
                        "CASH_SALE", java.math.BigDecimal.valueOf(order.getOrderTotal()),
                        order.getSalesUser(), "Sale #" + result.orderId(),
                        order.getClientId() > 0 ? order.getClientId() : null,
                        null, "ORDER", String.valueOf(result.orderId()));
            }
        }

        enqueueFinancePosSaleAfterCommit(companyId, order, result);

        log.info("Saved order {} for company {} branch {} with {} items", result.orderId(), companyId,
                order.getBranchId(), order.getOrderDetails().size());
        return result.orderId();
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

    @Transactional
    public String bounceBackProduct(BounceBackOrderRequest request, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");

        DbPosOrder.OrderBounceBackContext context = dbPosOrder.getBounceBackContext(
                request.getOdId(),
                request.getBranchId(),
                companyId);

        if (context == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_DETAIL_NOT_FOUND", "Order detail not found");
        }
        if (context.getBouncedBack() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_DETAIL_ALREADY_BOUNCED",
                    "Order detail already bounced back");
        }

        int fullBounceDiscount = context.getOrderDiscount() > 0 && !context.hasOtherActiveItems()
                ? context.getOrderDiscount()
                : 0;
        int refundAmount = context.getTotal() + fullBounceDiscount;
        int incomeReduction = (context.getTotal() - (context.getBuyingPrice() * context.getQuantity()))
                + fullBounceDiscount;
        Timestamp returnTime = new Timestamp(System.currentTimeMillis());
        Long inventoryMovementId = null;
        Long cashMovementId = null;

        int markedRows = dbPosOrder.markOrderDetailBouncedBack(request.getOdId(), request.getBranchId(), companyId,
                request.getToWho());
        if (markedRows != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_DETAIL_ALREADY_BOUNCED",
                    "Order detail already bounced back");
        }

        // Only restore inventory and record ledger if returning to stock
        if (request.getToWho() == 1 && context.getProductId() > 0) {
            int restoredRows = dbPosOrder.restoreInventoryQuantity(
                    context.getProductId(),
                    context.getQuantity(),
                    request.getBranchId(),
                    companyId);
            if (restoredRows != 1) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found for bounce back");
            }
            dbPosOrder.insertBounceBackInventoryTransaction(context, request.getBranchId(), companyId);
            inventoryMovementId = dbPosOrder.insertBounceBackLedgerEntry(context, request.getBranchId(), companyId);
            if (inventoryMovementId == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "BOUNCE_BACK_LEDGER_WRITE_FAILED",
                        "Modern inventory ledger entry was not written for bounce back");
            }
        }

        // If a shift is active, record the refund in the shift ledger
        com.example.valueinsoftbackend.Model.Shift.Shift activeShift = dbPosShiftPeriod.getActiveShift(companyId,
                request.getBranchId());
        if (activeShift != null) {
            cashMovementId = dbPosShiftPeriod.insertCashMovement(
                    companyId, activeShift.getShiftId(), request.getBranchId(),
                    "CASH_REFUND", java.math.BigDecimal.valueOf(refundAmount),
                    context.getSalesUser(), "Refund for Order Detail #" + request.getOdId(),
                    (context.getClientId() != null && context.getClientId() > 0) ? context.getClientId() : null,
                    null, "ORDER", String.valueOf(context.getOrderId()));
        }

        int updatedOrderRows = dbPosOrder.updateOrderBounceBackTotals(
                context.getOrderId(),
                request.getBranchId(),
                companyId,
                refundAmount,
                incomeReduction);
        if (updatedOrderRows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found for bounce back");
        }

        enqueueFinancePosSaleReturnAfterCommit(
                companyId,
                request.getBranchId(),
                context,
                refundAmount,
                request.getToWho() == 1,
                inventoryMovementId,
                cashMovementId,
                returnTime);

        log.info(
                "Bounced back order detail {} for company {} branch {} to {}",
                request.getOdId(),
                companyId,
                request.getBranchId(),
                request.getToWho());
        return "Bounce back successful";
    }

    private void enqueueFinancePosSaleReturnAfterCommit(int companyId,
                                                        int branchId,
                                                        DbPosOrder.OrderBounceBackContext context,
                                                        int refundAmount,
                                                        boolean returnedToStock,
                                                        Long inventoryMovementId,
                                                        Long cashMovementId,
                                                        Timestamp returnTime) {
        Runnable enqueue = () -> {
            try {
                financeOperationalPostingService.enqueuePosSaleReturn(
                        companyId,
                        branchId,
                        context,
                        refundAmount,
                        returnedToStock,
                        inventoryMovementId,
                        cashMovementId,
                        returnTime);
            } catch (RuntimeException exception) {
                log.warn(
                        "POS sale return for order detail {} saved for company {} branch {}, but finance posting request was not enqueued: {}",
                        context.getOrderDetailId(),
                        companyId,
                        branchId,
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

    public ArrayList<Order> getOrdersByPeriod(OrderPeriodRequest request, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosOrder.getOrdersByPeriod(
                request.branchId(),
                RequestTimestampParser.parse(request.startTime(), "startTime"),
                RequestTimestampParser.parse(request.endTime(), "endTime"),
                companyId);
    }

    private Order toOrder(CreateOrderRequest request) {
        ArrayList<OrderDetails> details = new ArrayList<>();
        List<OrderItemRequest> requestDetails = request.orderDetails();
        for (OrderItemRequest itemRequest : requestDetails) {
            details.add(new OrderDetails(
                    0,
                    itemRequest.itemId(),
                    itemRequest.itemName().trim(),
                    itemRequest.quantity(),
                    itemRequest.price(),
                    itemRequest.total(),
                    itemRequest.productId(),
                    0));
        }

        return new Order(
                request.orderId(),
                new Timestamp(System.currentTimeMillis()),
                request.clientName() == null ? "" : request.clientName().trim(),
                request.orderType().trim(),
                request.orderDiscount(),
                request.orderTotal(),
                request.salesUser().trim(),
                request.branchId(),
                request.clientId(),
                request.orderIncome(),
                0,
                details);
    }
}
