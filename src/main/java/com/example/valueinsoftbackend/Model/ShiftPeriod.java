package com.example.valueinsoftbackend.Model;

import java.util.ArrayList;

public class ShiftPeriod {
    int shiftID;
    String StartTime;
    String EndTime;

    ArrayList<Order> orderShiftList;

    public ShiftPeriod(int shiftID, String startTime, String endTime, ArrayList<Order> orderShiftList) {
        this.shiftID = shiftID;;
        StartTime = startTime;
        EndTime = endTime;
        this.orderShiftList = orderShiftList;
    }

    public int getShiftID() {
        return shiftID;
    }
    public String getStartTime() {
        return StartTime;
    }
    public String getEndTime() {
        return EndTime;
    }
    public ArrayList<Order> getOrderShiftList() {
        return orderShiftList;
    }
    public void setOrderShiftList(ArrayList<Order> orderShiftList) {
        this.orderShiftList = orderShiftList;
    }

}
