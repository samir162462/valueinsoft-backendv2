package com.example.valueinsoftbackend.Model;

import java.util.ArrayList;

public class ShiftPeriod {
    int shiftID;
    String StartTime;
    String EndTime;
    ArrayList<OrderShift> orderShiftList;

    public ShiftPeriod(int shiftID, String startTime, String endTime, ArrayList<OrderShift> orderShiftList) {
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

    public ArrayList<OrderShift> getOrderShiftList() {
        return orderShiftList;
    }

    public void setOrderShiftList(ArrayList<OrderShift> orderShiftList) {
        this.orderShiftList = orderShiftList;
    }



}
