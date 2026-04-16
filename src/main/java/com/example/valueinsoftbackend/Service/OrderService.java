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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OrderService {

    private final DbPosOrder dbPosOrder;
    private final com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod dbPosShiftPeriod;

    public OrderService(DbPosOrder dbPosOrder, com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod dbPosShiftPeriod) {
        this.dbPosOrder = dbPosOrder;
        this.dbPosShiftPeriod = dbPosShiftPeriod;
    }

    @Transactional
    public int createOrder(CreateOrderRequest request, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        Order order = toOrder(request);
        DbPosOrder.AddOrderResult result = dbPosOrder.addOrder(order, companyId);
        
        // Resolve shiftId: Prefer the result from addOrder, fallback to searching for active shift
        Integer shiftId = result.shiftId();
        if (shiftId == null) {
            com.example.valueinsoftbackend.Model.Shift.Shift activeShift = dbPosShiftPeriod.getActiveShift(companyId, order.getBranchId());
            if (activeShift != null) {
                shiftId = activeShift.getShiftId();
            }
        }

        // Record as cash movement if shift is active
        // We handle 'Dirict' (standard POS), 'Direct', and Arabic 'مباشر' or just any non-empty type
        if (shiftId != null && order.getOrderTotal() > 0) {
            String type = order.getOrderType();
            boolean isStandardSale = "Dirict".equalsIgnoreCase(type) || "Direct".equalsIgnoreCase(type) || "مباشر".equals(type);
            
            if (isStandardSale) {
                dbPosShiftPeriod.insertCashMovement(
                        companyId, shiftId, order.getBranchId(),
                        "CASH_SALE", java.math.BigDecimal.valueOf(order.getOrderTotal()),
                        order.getSalesUser(), "Sale #" + result.orderId(), 
                        order.getClientId() > 0 ? order.getClientId() : null,
                        null, "ORDER", String.valueOf(result.orderId())
                );
            }
        }

        log.info("Saved order {} for company {} branch {} with {} items", result.orderId(), companyId, order.getBranchId(), order.getOrderDetails().size());
        return result.orderId();
    }

    @Transactional
    public String bounceBackProduct(BounceBackOrderRequest request, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");

        DbPosOrder.OrderBounceBackContext context = dbPosOrder.getBounceBackContext(
                request.getOdId(),
                request.getBranchId(),
                companyId
        );

        if (context == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_DETAIL_NOT_FOUND", "Order detail not found");
        }
        if (context.getBouncedBack() != 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_DETAIL_ALREADY_BOUNCED", "Order detail already bounced back");
        }

        int fullBounceDiscount = context.getOrderDiscount() > 0 && !context.hasOtherActiveItems()
                ? context.getOrderDiscount()
                : 0;
        int incomeReduction = (context.getTotal() - (context.getBuyingPrice() * context.getQuantity())) + fullBounceDiscount;

        int markedRows = dbPosOrder.markOrderDetailBouncedBack(request.getOdId(), request.getBranchId(), companyId, request.getToWho());
        if (markedRows != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_DETAIL_ALREADY_BOUNCED", "Order detail already bounced back");
        }

        // Only restore inventory and record ledger if returning to stock
        if (request.getToWho() == 1 && context.getProductId() > 0) {
            int restoredRows = dbPosOrder.restoreInventoryQuantity(
                    context.getProductId(),
                    context.getQuantity(),
                    request.getBranchId(),
                    companyId
            );
            if (restoredRows != 1) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found for bounce back");
            }
            dbPosOrder.insertBounceBackInventoryTransaction(context, request.getBranchId(), companyId);
            int modernLedgerRows = dbPosOrder.insertBounceBackLedgerEntry(context, request.getBranchId(), companyId);
            if (modernLedgerRows != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "BOUNCE_BACK_LEDGER_WRITE_FAILED",
                        "Modern inventory ledger entry was not written for bounce back"
                );
            }
        }

        // If a shift is active, record the refund in the shift ledger
        com.example.valueinsoftbackend.Model.Shift.Shift activeShift = dbPosShiftPeriod.getActiveShift(companyId, request.getBranchId());
        if (activeShift != null) {
            dbPosShiftPeriod.insertCashMovement(
                    companyId, activeShift.getShiftId(), request.getBranchId(),
                    "CASH_REFUND", java.math.BigDecimal.valueOf(context.getTotal() + fullBounceDiscount),
                    context.getSalesUser(), "Refund for Order Detail #" + request.getOdId(),
                    (context.getClientId() != null && context.getClientId() > 0) ? context.getClientId() : null,
                    null, "ORDER", String.valueOf(context.getOrderId())
            );
        }

        int updatedOrderRows = dbPosOrder.updateOrderBounceBackTotals(
                context.getOrderId(),
                request.getBranchId(),
                companyId,
                context.getTotal() + fullBounceDiscount,
                incomeReduction
        );
        if (updatedOrderRows != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found for bounce back");
        }

        log.info(
                "Bounced back order detail {} for company {} branch {} to {}",
                request.getOdId(),
                companyId,
                request.getBranchId(),
                request.getToWho()
        );
        return "Bounce back successful";
    }

    public ArrayList<Order> getOrdersByPeriod(OrderPeriodRequest request, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosOrder.getOrdersByPeriod(
                request.getBranchId(),
                RequestTimestampParser.parse(request.getStartTime(), "startTime"),
                RequestTimestampParser.parse(request.getEndTime(), "endTime"),
                companyId
        );
    }

    private Order toOrder(CreateOrderRequest request) {
        ArrayList<OrderDetails> details = new ArrayList<>();
        List<OrderItemRequest> requestDetails = request.getOrderDetails();
        for (OrderItemRequest itemRequest : requestDetails) {
            details.add(new OrderDetails(
                    0,
                    itemRequest.getItemId(),
                    itemRequest.getItemName().trim(),
                    itemRequest.getQuantity(),
                    itemRequest.getPrice(),
                    itemRequest.getTotal(),
                    itemRequest.getProductId(),
                    0
            ));
        }

        return new Order(
                request.getOrderId(),
                new Timestamp(System.currentTimeMillis()),
                request.getClientName() == null ? "" : request.getClientName().trim(),
                request.getOrderType().trim(),
                request.getOrderDiscount(),
                request.getOrderTotal(),
                request.getSalesUser().trim(),
                request.getBranchId(),
                request.getClientId(),
                request.getOrderIncome(),
                0,
                details
        );
    }
}
