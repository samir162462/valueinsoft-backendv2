package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Service.finance.FinanceOperationalPostingService;
import com.example.valueinsoftbackend.Service.finance.PaymentTypeClassifier;
import com.example.valueinsoftbackend.Service.openitems.ArCreditNoteService;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;
import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbBranchSettings;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.OrderDetails;
import com.example.valueinsoftbackend.Model.Request.BounceBackOrderRequest;
import com.example.valueinsoftbackend.Model.Request.CreateOrderRequest;
import com.example.valueinsoftbackend.Model.Request.OrderItemRequest;
import com.example.valueinsoftbackend.Model.Request.OrderPeriodRequest;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyRecordedEarn;
import com.example.valueinsoftbackend.loyalty.dto.LoyaltyReversalResult;
import com.example.valueinsoftbackend.loyalty.service.LoyaltyService;
import com.example.valueinsoftbackend.util.RequestTimestampParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final PosSalePostingService posSalePostingService;
    private final DbBranchSettings dbBranchSettings;
    private final LoyaltyService loyaltyService;
    private ArCreditNoteService arCreditNoteService;

    public OrderService(DbPosOrder dbPosOrder,
            com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod dbPosShiftPeriod,
            FinanceOperationalPostingService financeOperationalPostingService,
            PosSalePostingService posSalePostingService,
            DbBranchSettings dbBranchSettings,
            LoyaltyService loyaltyService) {
        this.dbPosOrder = dbPosOrder;
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.financeOperationalPostingService = financeOperationalPostingService;
        this.posSalePostingService = posSalePostingService;
        this.dbBranchSettings = dbBranchSettings;
        this.loyaltyService = loyaltyService;
    }

    @Autowired
    void setArCreditNoteService(ArCreditNoteService arCreditNoteService) {
        this.arCreditNoteService = arCreditNoteService;
    }

    @Transactional
    public com.example.valueinsoftbackend.Model.Response.CreateOrderResult createOrder(CreateOrderRequest request, int companyId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        Order order = toOrder(request);
        if (posSalePostingService != null) {
            return posSalePostingService.postSale(companyId, order);
        }
        com.example.valueinsoftbackend.Model.Response.CreateOrderResult result = dbPosOrder.addOrder(order, companyId);
        
        if (result.idempotencyHit()) {
            log.info("Idempotency hit detected in OrderService, skipping downstream posting for order {} receipt {}", result.orderId(), result.receiptNumber());
            return result;
        }

        confirmLoyaltyRedemption(companyId, order, result);
        recordLoyaltyEarn(companyId, order, result);

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

    private void enqueueFinancePosSaleAfterCommit(int companyId, Order order, com.example.valueinsoftbackend.Model.Response.CreateOrderResult result) {
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
        String returnReason = request.getReason() == null || request.getReason().isBlank()
                ? "Reason not provided"
                : request.getReason().trim();
        if (request.getToWho() == 2 && !isSupplierReturnsEnabled(companyId, request.getBranchId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SUPPLIER_RETURNS_DISABLED",
                    "Supplier returns are disabled for this branch");
        }

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
        boolean creditReturn = PaymentTypeClassifier.classify(context.getOrderType()).category()
                == PaymentTypeClassifier.Category.RECEIVABLE;

        int markedRows = dbPosOrder.markOrderDetailBouncedBack(request.getOdId(), request.getBranchId(), companyId,
                request.getToWho());
        if (markedRows != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_DETAIL_ALREADY_BOUNCED",
                    "Order detail already bounced back");
        }

        // Only restore inventory and record ledger if returning to stock
        if (request.getToWho() == 1 && context.getProductId() > 0) {
            if (dbPosOrder.isSerializedProduct(companyId, context.getProductId())) {
                dbPosOrder.returnSerializedUnitsForOrderDetail(context, request.getBranchId(), companyId);
            } else {
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
        }

        if (creditReturn && context.getClientId() != null && context.getClientId() > 0 && arCreditNoteService != null) {
            String currency = arCreditNoteService.companyCurrency(companyId);
            OpenItemsWriteModels.NoteResult note = arCreditNoteService.create(companyId,
                    new OpenItemsWriteModels.NoteCreateCommand(
                            request.getBranchId(), context.getClientId(), "POS credit return",
                            "ORDER_DETAIL", (long) context.getOrderDetailId(), currency,
                            java.math.BigDecimal.valueOf(refundAmount),
                            "bounce:" + context.getOrderDetailId(),
                            "Return for order " + context.getOrderId() + ": " + returnReason),
                    context.getSalesUser());
            arCreditNoteService.apply(companyId, request.getBranchId(), context.getClientId(), note.noteId(),
                    new OpenItemsWriteModels.AllocationCommand(currency,
                            "bounce-apply:" + context.getOrderDetailId(), List.of()), context.getSalesUser());
        }

        // Cash movement exists only when money was actually refunded.
        com.example.valueinsoftbackend.Model.Shift.Shift activeShift = dbPosShiftPeriod.getActiveShift(companyId,
                request.getBranchId());
        if (activeShift != null && !creditReturn) {
            cashMovementId = dbPosShiftPeriod.insertCashMovement(
                    companyId, activeShift.getShiftId(), request.getBranchId(),
                    "CASH_REFUND", java.math.BigDecimal.valueOf(refundAmount),
                    context.getSalesUser(), "Refund for Order Detail #" + request.getOdId() + ": " + returnReason,
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

        reverseLoyaltyForBounceBack(companyId, request.getBranchId(), context, refundAmount);

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
                "Bounced back order detail {} for company {} branch {} to {}. Reason: {}",
                request.getOdId(),
                companyId,
                request.getBranchId(),
                request.getToWho(),
                returnReason);
        return "Bounce back successful";
    }

    private void reverseLoyaltyForBounceBack(int companyId,
                                             int branchId,
                                             DbPosOrder.OrderBounceBackContext context,
                                             int refundAmount) {
        if (loyaltyService == null) {
            return;
        }
        try {
            LoyaltyReversalResult reversal = loyaltyService.reverseOrderDetailReturn(
                    companyId,
                    branchId,
                    context,
                    refundAmount,
                    !context.hasOtherActiveItems());
            if (reversal.inserted()) {
                log.info(
                        "Reversed loyalty for company {} branch {} order {} detail {}: earned={} restored={}",
                        companyId,
                        branchId,
                        context.getOrderId(),
                        context.getOrderDetailId(),
                        reversal.earnedPointsReversed(),
                        reversal.redeemedPointsRestored());
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "Order detail {} was bounced for company {} branch {}, but loyalty reversal failed: {}",
                    context.getOrderDetailId(),
                    companyId,
                    branchId,
                    exception.getMessage());
        }
    }

    private boolean isSupplierReturnsEnabled(int companyId, int branchId) {
        if (dbBranchSettings == null) {
            return true;
        }

        Object value = dbBranchSettings.getEffectiveValueMap(companyId, branchId).get("inventory.allowSupplierReturns");
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return true;
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
            List<String> unitIdentifiers = new ArrayList<>(itemRequest.unitIdentifiers());
            if (unitIdentifiers.isEmpty() && itemRequest.serial() != null && !itemRequest.serial().trim().isEmpty()) {
                unitIdentifiers.add(itemRequest.serial().trim());
            }
            details.add(new OrderDetails(
                    0,
                    itemRequest.itemId(),
                    itemRequest.itemName().trim(),
                    itemRequest.quantity(),
                    itemRequest.price(),
                    itemRequest.total(),
                    itemRequest.productId(),
                    0,
                    itemRequest.productUnitIds(),
                    unitIdentifiers));
        }

        Order order = new Order(
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
        order.setLoyaltyRedemptionId(request.loyaltyRedemptionId());
        order.setLoyaltyPointsRedeemed(request.loyaltyPointsRedeemed() == null ? 0 : request.loyaltyPointsRedeemed());
        order.setLoyaltyPointsEarned(request.loyaltyPointsEarned() == null ? 0 : request.loyaltyPointsEarned());
        order.setLoyaltyDiscountAmount(request.loyaltyDiscountAmount());
        order.setLoyaltyNetAmount(request.loyaltyNetAmount());
        order.setPaidNowAmount(request.paidNowAmount());
        order.setReceivablePartyType(request.receivablePartyType());
        order.setReceivableSupplierId(request.receivableSupplierId());
        return order;
    }
}

