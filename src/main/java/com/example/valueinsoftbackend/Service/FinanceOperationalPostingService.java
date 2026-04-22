package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceSetup;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.Model.InventoryTransaction;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.DamagedItem;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Request.Finance.FinancePostingRequestCreateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FinanceOperationalPostingService {

    private static final String DEFAULT_CURRENCY_CODE = "EGP";

    private final DbFinanceSetup dbFinanceSetup;
    private final FinancePostingRequestService financePostingRequestService;

    public FinanceOperationalPostingService(DbFinanceSetup dbFinanceSetup,
                                            FinancePostingRequestService financePostingRequestService) {
        this.dbFinanceSetup = dbFinanceSetup;
        this.financePostingRequestService = financePostingRequestService;
    }

    public void enqueuePosSale(int companyId,
                               Order order,
                               int orderId,
                               Timestamp orderTime,
                               List<DbPosOrder.OrderFinanceCostLine> costLines) {
        LocalDate postingDate = orderTime.toLocalDateTime().toLocalDate();
        UUID fiscalPeriodId = dbFinanceSetup.findPostingFiscalPeriodIdForDate(companyId, postingDate);
        if (fiscalPeriodId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_POSTING_PERIOD_NOT_FOUND",
                    "No open or soft-locked finance fiscal period exists for POS posting date");
        }

        FinancePostingRequestCreateRequest request = new FinancePostingRequestCreateRequest(
                companyId,
                order.getBranchId(),
                "pos",
                "sale",
                "order-" + orderId,
                postingDate,
                fiscalPeriodId,
                buildPosSalePayload(order, orderId, costLines));

        financePostingRequestService.createPostingRequestFromSystem(order.getSalesUser(), request);
    }

    public void enqueuePosSaleReturn(int companyId,
                                     int branchId,
                                     DbPosOrder.OrderBounceBackContext context,
                                     int refundAmount,
                                     boolean returnedToStock,
                                     Long inventoryMovementId,
                                     Long cashMovementId,
                                     Timestamp returnTime) {
        if (context == null || refundAmount <= 0) {
            return;
        }

        LocalDate postingDate = returnTime.toLocalDateTime().toLocalDate();
        UUID fiscalPeriodId = dbFinanceSetup.findPostingFiscalPeriodIdForDate(companyId, postingDate);
        if (fiscalPeriodId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_POSTING_PERIOD_NOT_FOUND",
                    "No open or soft-locked finance fiscal period exists for POS return posting date");
        }

        FinancePostingRequestCreateRequest request = new FinancePostingRequestCreateRequest(
                companyId,
                branchId,
                "pos",
                "sale_return",
                "order-detail-" + context.getOrderDetailId(),
                postingDate,
                fiscalPeriodId,
                buildPosSaleReturnPayload(context, refundAmount, returnedToStock, inventoryMovementId, cashMovementId));

        financePostingRequestService.createPostingRequestFromSystem(context.getSalesUser(), request);
    }

    public void enqueuePurchaseInventoryTransaction(int companyId,
                                                    int branchId,
                                                    InventoryTransaction transaction,
                                                    int inventoryTransactionId,
                                                    Long inventoryMovementId) {
        if (transaction.getTransTotal() <= 0 || transaction.getNumItems() <= 0) {
            return;
        }

        LocalDate postingDate = transaction.getTime().toLocalDateTime().toLocalDate();
        UUID fiscalPeriodId = dbFinanceSetup.findPostingFiscalPeriodIdForDate(companyId, postingDate);
        if (fiscalPeriodId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_POSTING_PERIOD_NOT_FOUND",
                    "No open or soft-locked finance fiscal period exists for purchase posting date");
        }

        FinancePostingRequestCreateRequest request = new FinancePostingRequestCreateRequest(
                companyId,
                branchId,
                "purchase",
                "purchase_invoice",
                "inventory-transaction-" + inventoryTransactionId,
                postingDate,
                fiscalPeriodId,
                buildPurchasePayload(transaction, inventoryTransactionId, inventoryMovementId));

        financePostingRequestService.createPostingRequestFromSystem(transaction.getUserName(), request);
    }

    public void enqueueInventoryAdjustmentTransaction(int companyId,
                                                      int branchId,
                                                      InventoryTransaction transaction,
                                                      int inventoryTransactionId,
                                                      Long inventoryMovementId) {
        if (transaction.getTransTotal() == 0 || transaction.getNumItems() == 0) {
            return;
        }

        LocalDate postingDate = transaction.getTime().toLocalDateTime().toLocalDate();
        UUID fiscalPeriodId = dbFinanceSetup.findPostingFiscalPeriodIdForDate(companyId, postingDate);
        if (fiscalPeriodId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_POSTING_PERIOD_NOT_FOUND",
                    "No open or soft-locked finance fiscal period exists for inventory adjustment posting date");
        }

        FinancePostingRequestCreateRequest request = new FinancePostingRequestCreateRequest(
                companyId,
                branchId,
                "inventory",
                "inventory_adjustment",
                "inventory-transaction-" + inventoryTransactionId,
                postingDate,
                fiscalPeriodId,
                buildInventoryAdjustmentPayload(transaction, inventoryTransactionId, inventoryMovementId));

        financePostingRequestService.createPostingRequestFromSystem(transaction.getUserName(), request);
    }

    public void enqueueDamagedItem(int companyId,
                                   int branchId,
                                   DamagedItem damagedItem,
                                   int damagedItemId,
                                   Long inventoryMovementId) {
        if (damagedItem.getAmountTP() <= 0 || damagedItem.getQuantity() <= 0) {
            return;
        }

        LocalDate postingDate = damagedItem.getTime().toLocalDateTime().toLocalDate();
        UUID fiscalPeriodId = dbFinanceSetup.findPostingFiscalPeriodIdForDate(companyId, postingDate);
        if (fiscalPeriodId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_POSTING_PERIOD_NOT_FOUND",
                    "No open or soft-locked finance fiscal period exists for damaged inventory posting date");
        }

        FinancePostingRequestCreateRequest request = new FinancePostingRequestCreateRequest(
                companyId,
                branchId,
                "inventory",
                "damage",
                "damaged-item-" + damagedItemId,
                postingDate,
                fiscalPeriodId,
                buildDamagedItemPayload(damagedItem, damagedItemId, inventoryMovementId));

        financePostingRequestService.createPostingRequestFromSystem(damagedItem.getCashierUser(), request);
    }

    public void enqueueCashSafeDrop(int companyId,
                                    int branchId,
                                    int shiftId,
                                    Long cashMovementId,
                                    BigDecimal amount,
                                    Timestamp movementTime,
                                    String actorName) {
        enqueuePaymentSettlement(
                companyId,
                branchId,
                "safe_drop",
                "cash-movement-" + cashMovementId,
                amount,
                "cash",
                "safe",
                "cash-movement-" + cashMovementId,
                movementTime,
                actorName,
                Map.of("shiftId", shiftId, "cashMovementId", cashMovementId));
    }

    public void enqueueCashDrawerClose(int companyId,
                                       int branchId,
                                       int shiftId,
                                       Long cashMovementId,
                                       BigDecimal amount,
                                       Timestamp movementTime,
                                       String actorName) {
        enqueuePaymentSettlement(
                companyId,
                branchId,
                "cash_drawer_close",
                "shift-close-" + shiftId,
                amount,
                "cash",
                "safe",
                "shift-close-" + shiftId,
                movementTime,
                actorName,
                Map.of("shiftId", shiftId, "cashMovementId", cashMovementId));
    }

    private void enqueuePaymentSettlement(int companyId,
                                          int branchId,
                                          String sourceType,
                                          String sourceId,
                                          BigDecimal amount,
                                          String settlementMethod,
                                          String destination,
                                          String paymentId,
                                          Timestamp movementTime,
                                          String actorName,
                                          Map<String, Object> extraPayload) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        LocalDate postingDate = movementTime.toLocalDateTime().toLocalDate();
        UUID fiscalPeriodId = dbFinanceSetup.findPostingFiscalPeriodIdForDate(companyId, postingDate);
        if (fiscalPeriodId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "FINANCE_POSTING_PERIOD_NOT_FOUND",
                    "No open or soft-locked finance fiscal period exists for payment settlement posting date");
        }

        FinancePostingRequestCreateRequest request = new FinancePostingRequestCreateRequest(
                companyId,
                branchId,
                "payment",
                sourceType,
                sourceId,
                postingDate,
                fiscalPeriodId,
                buildPaymentSettlementPayload(amount, settlementMethod, destination, paymentId, extraPayload));

        financePostingRequestService.createPostingRequestFromSystem(actorName, request);
    }

    private Map<String, Object> buildPosSalePayload(Order order,
                                                    int orderId,
                                                    List<DbPosOrder.OrderFinanceCostLine> costLines) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("currencyCode", DEFAULT_CURRENCY_CODE);
        payload.put("orderId", orderId);
        payload.put("customerId", positiveOrNull(order.getClientId()));
        payload.put("netAmount", money(order.getOrderTotal()));
        payload.put("discountAmount", money(order.getOrderDiscount()));
        payload.put("taxAmount", money(0));
        payload.put("paymentMethod", order.getOrderType());
        payload.put("salesUser", order.getSalesUser());
        payload.put("items", buildItemPayload(costLines));
        return payload;
    }

    private Map<String, Object> buildPosSaleReturnPayload(DbPosOrder.OrderBounceBackContext context,
                                                          int refundAmount,
                                                          boolean returnedToStock,
                                                          Long inventoryMovementId,
                                                          Long cashMovementId) {
        BigDecimal returnedCost = money(BigDecimal.valueOf((long) context.getBuyingPrice() * context.getQuantity()));

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("currencyCode", DEFAULT_CURRENCY_CODE);
        payload.put("originalOrderId", context.getOrderId());
        payload.put("orderDetailId", context.getOrderDetailId());
        payload.put("customerId", context.getClientId());
        payload.put("refundAmount", money(refundAmount));
        payload.put("salesReturnAmount", money(refundAmount));
        payload.put("taxAmount", money(0));
        payload.put("refundMethod", "cash");
        payload.put("returnedToStock", returnedToStock);
        payload.put("paymentId", cashMovementId == null ? null : "cash-movement-" + cashMovementId);
        payload.put("inventoryMovementId", inventoryMovementId);
        payload.put("items", buildReturnItemPayload(context, returnedCost, inventoryMovementId));
        return payload;
    }

    private List<Map<String, Object>> buildReturnItemPayload(DbPosOrder.OrderBounceBackContext context,
                                                             BigDecimal returnedCost,
                                                             Long inventoryMovementId) {
        if (context.getProductId() <= 0 || context.getQuantity() <= 0 || returnedCost.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("productId", context.getProductId());
        item.put("quantity", context.getQuantity());
        item.put("unitCost", money(context.getBuyingPrice()));
        item.put("totalCost", returnedCost);
        item.put("inventoryMovementId", inventoryMovementId);
        return List.of(item);
    }

    private Map<String, Object> buildPurchasePayload(InventoryTransaction transaction,
                                                     int inventoryTransactionId,
                                                     Long inventoryMovementId) {
        BigDecimal inventoryAmount = money(transaction.getTransTotal());
        BigDecimal remainingAmount = money(Math.max(transaction.getRemainingAmount(), 0));
        BigDecimal paidAmount = inventoryAmount.subtract(remainingAmount).setScale(4, RoundingMode.HALF_UP);
        if (paidAmount.compareTo(BigDecimal.ZERO) < 0) {
            paidAmount = BigDecimal.ZERO.setScale(4);
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("currencyCode", DEFAULT_CURRENCY_CODE);
        payload.put("inventoryTransactionId", inventoryTransactionId);
        payload.put("supplierId", positiveOrNull(transaction.getSupplierId()));
        payload.put("inventoryAmount", inventoryAmount);
        payload.put("taxAmount", money(0));
        payload.put("grossAmount", inventoryAmount);
        payload.put("paidAmount", paidAmount);
        payload.put("paymentMethod", transaction.getPayType());
        payload.put("paymentId", inventoryTransactionId > 0 ? "inventory-transaction-" + inventoryTransactionId : null);
        payload.put("inventoryMovementId", inventoryMovementId);
        payload.put("items", buildPurchaseItemPayload(transaction, inventoryMovementId));
        return payload;
    }

    private List<Map<String, Object>> buildPurchaseItemPayload(InventoryTransaction transaction,
                                                               Long inventoryMovementId) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("productId", transaction.getProductId());
        item.put("quantity", transaction.getNumItems());
        item.put("totalCost", money(transaction.getTransTotal()));
        item.put("inventoryMovementId", inventoryMovementId);
        return List.of(item);
    }

    private Map<String, Object> buildInventoryAdjustmentPayload(InventoryTransaction transaction,
                                                               int inventoryTransactionId,
                                                               Long inventoryMovementId) {
        String direction = transaction.getNumItems() > 0 ? "increase" : "decrease";
        BigDecimal adjustmentAmount = money(BigDecimal.valueOf(Math.abs((long) transaction.getTransTotal())));

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("currencyCode", DEFAULT_CURRENCY_CODE);
        payload.put("inventoryTransactionId", inventoryTransactionId);
        payload.put("direction", direction);
        payload.put("quantityDelta", transaction.getNumItems());
        payload.put("adjustmentAmount", adjustmentAmount);
        payload.put("reasonCode", "manual_inventory_transaction");
        payload.put("reason", transaction.getTransactionType());
        payload.put("inventoryMovementId", inventoryMovementId);
        payload.put("items", buildInventoryAdjustmentItemPayload(
                transaction.getProductId(),
                transaction.getNumItems(),
                adjustmentAmount,
                inventoryMovementId));
        return payload;
    }

    private Map<String, Object> buildDamagedItemPayload(DamagedItem damagedItem,
                                                        int damagedItemId,
                                                        Long inventoryMovementId) {
        BigDecimal adjustmentAmount = money(damagedItem.getAmountTP());

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("currencyCode", DEFAULT_CURRENCY_CODE);
        payload.put("damagedItemId", damagedItemId);
        payload.put("direction", "decrease");
        payload.put("quantityDelta", damagedItem.getQuantity() * -1);
        payload.put("adjustmentAmount", adjustmentAmount);
        payload.put("reasonCode", "damage");
        payload.put("reason", damagedItem.getReason());
        payload.put("inventoryMovementId", inventoryMovementId);
        payload.put("items", buildInventoryAdjustmentItemPayload(
                damagedItem.getProductId(),
                damagedItem.getQuantity() * -1,
                adjustmentAmount,
                inventoryMovementId));
        return payload;
    }

    private List<Map<String, Object>> buildInventoryAdjustmentItemPayload(int productId,
                                                                         int quantityDelta,
                                                                         BigDecimal adjustmentAmount,
                                                                         Long inventoryMovementId) {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId);
        item.put("quantityDelta", quantityDelta);
        item.put("totalCost", adjustmentAmount);
        item.put("inventoryMovementId", inventoryMovementId);
        return List.of(item);
    }

    private Map<String, Object> buildPaymentSettlementPayload(BigDecimal amount,
                                                              String settlementMethod,
                                                              String destination,
                                                              String paymentId,
                                                              Map<String, Object> extraPayload) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("currencyCode", DEFAULT_CURRENCY_CODE);
        payload.put("grossAmount", money(amount));
        payload.put("netAmount", money(amount));
        payload.put("feeAmount", money(0));
        payload.put("settlementMethod", settlementMethod);
        payload.put("destination", destination);
        payload.put("paymentId", paymentId);
        if (extraPayload != null) {
            payload.putAll(extraPayload);
        }
        return payload;
    }

    private List<Map<String, Object>> buildItemPayload(List<DbPosOrder.OrderFinanceCostLine> costLines) {
        if (costLines == null || costLines.isEmpty()) {
            return List.of();
        }

        ArrayList<Map<String, Object>> items = new ArrayList<>();
        for (DbPosOrder.OrderFinanceCostLine costLine : costLines) {
            if (costLine.productId() <= 0 || costLine.quantity() <= 0 || costLine.totalCost() == null) {
                continue;
            }
            if (costLine.totalCost().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("productId", costLine.productId());
            item.put("quantity", costLine.quantity());
            item.put("unitCost", money(costLine.unitCost()));
            item.put("totalCost", money(costLine.totalCost()));
            items.add(item);
        }
        return items;
    }

    private Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    private BigDecimal money(int value) {
        return money(BigDecimal.valueOf(value));
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
