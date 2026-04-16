package com.example.valueinsoftbackend.Model.Shift;

import com.example.valueinsoftbackend.Model.Order;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/**
 * @deprecated Use {@link Shift} for new development.
 */
@Deprecated
public class ShiftPeriod {
    int shiftID;
    Timestamp StartTime;
    Timestamp EndTime;

    ArrayList<Order> orderShiftList;
    List<Map<String, Object>> cashMovements;
    BigDecimal expectedCash;

    public ShiftPeriod(int shiftID, Timestamp startTime, Timestamp endTime, ArrayList<Order> orderShiftList) {
        this.shiftID = shiftID;
        StartTime = startTime;
        EndTime = endTime;
        this.orderShiftList = orderShiftList;
        this.cashMovements = new ArrayList<>();
        this.expectedCash = BigDecimal.ZERO;
    }

    public int getShiftID() {
        return shiftID;
    }

    public void setShiftID(int shiftID) {
        this.shiftID = shiftID;
    }

    public Timestamp getStartTime() {
        return StartTime;
    }

    public void setStartTime(Timestamp startTime) {
        StartTime = startTime;
    }

    public Timestamp getEndTime() {
        return EndTime;
    }

    public void setEndTime(Timestamp endTime) {
        EndTime = endTime;
    }

    public ArrayList<Order> getOrderShiftList() {
        return orderShiftList;
    }

    public void setOrderShiftList(ArrayList<Order> orderShiftList) {
        this.orderShiftList = orderShiftList;
    }

    public List<Map<String, Object>> getCashMovements() { return cashMovements; }
    public void setCashMovements(List<Map<String, Object>> cashMovements) { this.cashMovements = cashMovements; }

    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }
}
