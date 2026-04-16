package com.example.valueinsoftbackend.Model.Shift;

import com.example.valueinsoftbackend.Model.Order;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rich domain model for a POS shift session.
 * Replaces the anemic ShiftPeriod POJO with full lifecycle,
 * cashier tracking, and cash-reconciliation fields.
 */
public class Shift {

    // ── identity ────────────────────────────────────────
    private int shiftId;
    private int branchId;
    private String registerCode;

    // ── lifecycle ───────────────────────────────────────
    private String status;            // OPEN, CLOSING, CLOSED, FORCE_CLOSED
    private Timestamp openedAt;
    private Timestamp closedAt;
    private String openedByUserId;
    private String assignedCashierId;
    private String closedByUserId;

    // ── cash reconciliation ────────────────────────────
    private BigDecimal openingFloat;
    private BigDecimal expectedCash;
    private BigDecimal countedCash;
    private BigDecimal varianceAmount;
    private String varianceReason;
    private String closeNote;

    // ── close-time snapshot ────────────────────────────
    private int orderCount;
    private BigDecimal grossSales;
    private BigDecimal netSales;
    private BigDecimal discountTotal;
    private BigDecimal refundTotal;

    // ── optimistic lock ────────────────────────────────
    private int version;

    // ── timestamps ─────────────────────────────────────
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // ── child collections (transient — populated on demand) ──
    private ArrayList<Order> orders;
    private List<Map<String, Object>> cashMovements;

    public Shift() {
    }

    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public String getRegisterCode() { return registerCode; }
    public void setRegisterCode(String registerCode) { this.registerCode = registerCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getOpenedAt() { return openedAt; }
    public void setOpenedAt(Timestamp openedAt) { this.openedAt = openedAt; }

    public Timestamp getClosedAt() { return closedAt; }
    public void setClosedAt(Timestamp closedAt) { this.closedAt = closedAt; }

    public String getOpenedByUserId() { return openedByUserId; }
    public void setOpenedByUserId(String openedByUserId) { this.openedByUserId = openedByUserId; }

    public String getAssignedCashierId() { return assignedCashierId; }
    public void setAssignedCashierId(String assignedCashierId) { this.assignedCashierId = assignedCashierId; }

    public String getClosedByUserId() { return closedByUserId; }
    public void setClosedByUserId(String closedByUserId) { this.closedByUserId = closedByUserId; }

    public BigDecimal getOpeningFloat() { return openingFloat; }
    public void setOpeningFloat(BigDecimal openingFloat) { this.openingFloat = openingFloat; }

    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }

    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }

    public BigDecimal getVarianceAmount() { return varianceAmount; }
    public void setVarianceAmount(BigDecimal varianceAmount) { this.varianceAmount = varianceAmount; }

    public String getVarianceReason() { return varianceReason; }
    public void setVarianceReason(String varianceReason) { this.varianceReason = varianceReason; }

    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String closeNote) { this.closeNote = closeNote; }

    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }

    public BigDecimal getGrossSales() { return grossSales; }
    public void setGrossSales(BigDecimal grossSales) { this.grossSales = grossSales; }

    public BigDecimal getNetSales() { return netSales; }
    public void setNetSales(BigDecimal netSales) { this.netSales = netSales; }

    public BigDecimal getDiscountTotal() { return discountTotal; }
    public void setDiscountTotal(BigDecimal discountTotal) { this.discountTotal = discountTotal; }

    public BigDecimal getRefundTotal() { return refundTotal; }
    public void setRefundTotal(BigDecimal refundTotal) { this.refundTotal = refundTotal; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public ArrayList<Order> getOrders() { return orders; }
    public void setOrders(ArrayList<Order> orders) { this.orders = orders; }

    public List<Map<String, Object>> getCashMovements() { return cashMovements; }
    public void setCashMovements(List<Map<String, Object>> cashMovements) { this.cashMovements = cashMovements; }
}
