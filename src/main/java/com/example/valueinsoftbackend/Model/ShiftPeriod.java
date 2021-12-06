package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;
import java.util.ArrayList;

public class ShiftPeriod {
    int shiftID;
    Timestamp StartTime;
    Timestamp EndTime;

    ArrayList<Order> orderShiftList;

    public ShiftPeriod(int shiftID, Timestamp startTime, Timestamp endTime, ArrayList<Order> orderShiftList) {
        this.shiftID = shiftID;
        StartTime = startTime;
        EndTime = endTime;
        this.orderShiftList = orderShiftList;
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
}
