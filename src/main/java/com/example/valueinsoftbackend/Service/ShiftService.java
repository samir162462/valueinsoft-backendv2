package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.Model.Request.*;
import lombok.extern.slf4j.Slf4j;

import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosOrder;
import com.example.valueinsoftbackend.DatabaseRequests.DbPOS.DbPosShiftPeriod;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Order;
import com.example.valueinsoftbackend.Model.Shift.Shift;
import com.example.valueinsoftbackend.Model.Shift.ShiftPeriod;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class ShiftService {

    private static final Set<String> ALLOWED_MOVEMENT_TYPES =
            Set.of("PAID_IN", "PAID_OUT", "SAFE_DROP", "CASH_ADJUSTMENT");

    private final DbPosShiftPeriod dbPosShiftPeriod;
    private final DbPosOrder dbPosOrder;
    private final ClientReceiptService clientReceiptService;

    public ShiftService(DbPosShiftPeriod dbPosShiftPeriod, 
                        DbPosOrder dbPosOrder,
                        ClientReceiptService clientReceiptService) {
        this.dbPosShiftPeriod = dbPosShiftPeriod;
        this.dbPosOrder = dbPosOrder;
        this.clientReceiptService = clientReceiptService;
    }

    // ── lifecycle ───────────────────────────────────────

    /**
     * Opens a new shift with opening float, cashier tracking, and audit logging.
     */
    @Transactional
    public Shift openShift(int companyId, OpenShiftRequest request, String principalName) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");

        Shift existing = dbPosShiftPeriod.getActiveShift(companyId, request.getBranchId());
        if (existing != null) {
            return existing;
        }

        BigDecimal openingFloat = BigDecimal.valueOf(request.getOpeningFloat());
        Timestamp now = new Timestamp(System.currentTimeMillis());

        Shift shift = dbPosShiftPeriod.insertShift(
                companyId,
                request.getBranchId(),
                now,
                principalName,
                openingFloat,
                request.getRegisterCode()
        );

        dbPosShiftPeriod.insertCashMovement(
                companyId, shift.getShiftId(), shift.getBranchId(),
                "OPENING_FLOAT", openingFloat, principalName,
                "Opening float set at shift start", null, null
        );

        dbPosShiftPeriod.insertShiftEvent(
                companyId, shift.getShiftId(), shift.getBranchId(),
                "SHIFT_OPENED", principalName, null, null,
                "{\"openingFloat\":" + openingFloat + "}"
        );

        log.info("Opened shift {} for company {} branch {} by {}",
                shift.getShiftId(), companyId, request.getBranchId(), principalName);
        return shift;
    }

    public Shift getActiveShift(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosShiftPeriod.getActiveShift(companyId, branchId);
    }

    public Shift getShiftById(int companyId, int shiftId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosShiftPeriod.getShiftById(companyId, shiftId);
    }

    public ArrayList<Shift> getBranchShifts(int companyId, int branchId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosShiftPeriod.getBranchShifts(companyId, branchId);
    }

    public List<Map<String, Object>> getShiftEvents(int companyId, int shiftId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        return dbPosShiftPeriod.getShiftEvents(companyId, shiftId);
    }

    public Map<String, Object> getReconciliation(int companyId, int shiftId) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");

        BigDecimal expectedCash = dbPosShiftPeriod.computeExpectedCash(companyId, shiftId);
        List<Map<String, Object>> movements = dbPosShiftPeriod.getCashMovements(companyId, shiftId);

        Map<String, Object> result = new HashMap<>();
        result.put("expectedCash", expectedCash);
        result.put("cashMovements", movements);
        return result;
    }

    /**
     * Records a manual cash movement (PAID_IN, PAID_OUT, SAFE_DROP) during a shift.
     * Links to ClientReceipt if clientId is present.
     */
    @Transactional
    public Map<String, Object> recordCashMovement(int companyId, int shiftId,
                                                  CashMovementRequest request,
                                                  String principalName) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");
        String type = request.getMovementType();
        if (!ALLOWED_MOVEMENT_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MOVEMENT_TYPE",
                    "Allowed types: " + ALLOWED_MOVEMENT_TYPES);
        }

        Shift shift = dbPosShiftPeriod.getShiftById(companyId, shiftId);
        if (!"OPEN".equals(shift.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIFT_NOT_OPEN", "Shift is not active");
        }

        BigDecimal amount = BigDecimal.valueOf(request.getAmount());
        dbPosShiftPeriod.insertCashMovement(
                companyId, shiftId, shift.getBranchId(),
                type, amount, principalName, request.getNote(),
                (request.getClientId() != null && request.getClientId() > 0) ? request.getClientId() : null,
                request.getAssociatedUserId()
        );

        // Link to Client Receipt if clientId is present (Sync accounts)
        if (request.getClientId() != null && request.getClientId() > 0) {
            String receiptType = null;
            if ("PAID_IN".equals(type)) receiptType = "Payment";
            else if ("PAID_OUT".equals(type)) receiptType = "Refund";

            if (receiptType != null) {
                CreateClientReceiptRequest receiptReq = new CreateClientReceiptRequest();
                receiptReq.setClientId(request.getClientId());
                receiptReq.setBranchId(shift.getBranchId());
                receiptReq.setAmount(amount);
                receiptReq.setType(receiptType);
                receiptReq.setUserName(principalName);
                clientReceiptService.addClientReceipt(companyId, receiptReq);
            }
        }

        dbPosShiftPeriod.insertShiftEvent(
                companyId, shiftId, shift.getBranchId(),
                "CASH_MOVEMENT", principalName, null, type,
                "{\"amount\":" + amount + ",\"type\":\"" + type + "\"}"
        );

        Map<String, Object> result = new HashMap<>();
        result.put("shiftId", shiftId);
        result.put("movementType", type);
        result.put("amount", amount);
        return result;
    }

    /**
     * Closes a shift with server-side cash reconciliation and balance validation.
     */
    @Transactional
    public Shift closeShiftWithReconciliation(int companyId, int shiftId,
                                               CloseShiftRequest request,
                                               String principalName) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");

        Shift shift = dbPosShiftPeriod.getShiftForUpdate(companyId, shiftId);
        if (!"OPEN".equals(shift.getStatus()) && !"CLOSING".equals(shift.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIFT_NOT_CLOSABLE",
                    "Shift is in status " + shift.getStatus() + " and cannot be closed");
        }

        BigDecimal expectedCash = dbPosShiftPeriod.computeExpectedCash(companyId, shiftId);
        BigDecimal countedCash = BigDecimal.valueOf(request.getCountedCash());
        BigDecimal variance = countedCash.subtract(expectedCash);

        ArrayList<Order> orders = dbPosOrder.getOrdersByShiftId(companyId, shift.getBranchId(), shiftId);

        int orderCount = orders.size();
        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        for (Order o : orders) {
            grossSales = grossSales.add(BigDecimal.valueOf(o.getOrderTotal() + o.getOrderDiscount()));
            discountTotal = discountTotal.add(BigDecimal.valueOf(o.getOrderDiscount()));
            refundTotal = refundTotal.add(BigDecimal.valueOf(o.getTotalBouncedBack()));
        }
        BigDecimal netSales = grossSales.subtract(discountTotal).subtract(refundTotal);

        Timestamp now = new Timestamp(System.currentTimeMillis());
        String closeStatus = "CLOSED";

        int rows = dbPosShiftPeriod.closeShift(
                companyId, shiftId, now, principalName, closeStatus,
                expectedCash, countedCash, variance,
                request.getVarianceReason(), request.getCloseNote(),
                orderCount, grossSales, netSales, discountTotal, refundTotal
        );

        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SHIFT_CLOSE_FAILED",
                    "Shift could not be closed — it may have been closed by another user");
        }

        dbPosShiftPeriod.insertCashMovement(
                companyId, shiftId, shift.getBranchId(),
                "CLOSE_COUNT", countedCash, principalName,
                "Physical drawer count at shift close", null, null
        );

        dbPosShiftPeriod.insertShiftEvent(
                companyId, shiftId, shift.getBranchId(),
                "SHIFT_CLOSED", principalName, null, request.getCloseNote(),
                "{\"expectedCash\":" + expectedCash +
                        ",\"countedCash\":" + countedCash +
                        ",\"variance\":" + variance +
                        "}"
        );

        log.info("Closed shift {} with variance {}", shiftId, variance);
        return dbPosShiftPeriod.getShiftById(companyId, shiftId);
    }

    @Transactional
    public Shift forceCloseShift(int companyId, int shiftId, String reason, String principalName) {
        TenantSqlIdentifiers.requirePositive(companyId, "companyId");

        Shift shift = dbPosShiftPeriod.getShiftForUpdate(companyId, shiftId);
        Timestamp now = new Timestamp(System.currentTimeMillis());

        dbPosShiftPeriod.forceCloseShift(companyId, shiftId, now, principalName, reason);

        dbPosShiftPeriod.insertShiftEvent(
                companyId, shiftId, shift.getBranchId(),
                "SHIFT_FORCE_CLOSED", principalName, null, reason,
                "{\"status\":\"FORCE_CLOSED\"}"
        );

        log.warn("Shift {} force-closed by manager {}", shiftId, principalName);
        return dbPosShiftPeriod.getShiftById(companyId, shiftId);
    }

    // ── helpers ─────────────────────────────────────────

    public int getShiftBranchId(int companyId, int shiftId) {
        return dbPosShiftPeriod.getShiftBranchId(companyId, shiftId);
    }

    // ── legacy COMPATIBILITY ────────────────────────────

    @Deprecated
    public ResponseEntity<Object> startShift(int companyId, int branchId) {
        if (dbPosShiftPeriod.hasOpenShift(companyId, branchId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Shift already open");
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        dbPosShiftPeriod.insertShift(companyId, branchId, now);
        return ResponseEntity.ok("Shift started");
    }

    @Deprecated
    public String endShift(int companyId, int shiftId) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        dbPosShiftPeriod.closeShift(companyId, shiftId, now);
        return "Shift ended";
    }

    @Deprecated
    public ShiftPeriod currentShift(int companyId, CurrentShiftRequest data) {
        ShiftPeriod sp = dbPosShiftPeriod.getCurrentShift(companyId, data.getBranchId());
        if (sp != null) {
            // Enrich with details if requested
            if (data.isGetDetails()) {
                sp.setOrderShiftList(dbPosOrder.getOrdersByShiftId(companyId, data.getBranchId(), sp.getShiftID()));
                sp.setCashMovements(dbPosShiftPeriod.getCashMovements(companyId, sp.getShiftID()));
                sp.setExpectedCash(dbPosShiftPeriod.computeExpectedCash(companyId, sp.getShiftID()));
            }
        }
        return sp;
    }

    @Deprecated
    public ArrayList<Order> shiftOrdersById(int companyId, ShiftOrdersRequest data) {
        return dbPosOrder.getOrdersByShiftId(companyId, data.getBranchId(), data.getSpId());
    }

    @Deprecated
    public ArrayList<ShiftPeriod> shiftsByBranchId(int companyId, int branchId) {
        return dbPosShiftPeriod.getBranchShiftsLegacy(companyId, branchId);
    }
}
