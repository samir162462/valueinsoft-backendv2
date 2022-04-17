/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.AppModel;

import java.math.BigDecimal;
import java.sql.Date;

public class AppModelSubscription {

    int sId;
    Date startTime;
    Date endTime;
    int branchId;
    BigDecimal amountToPay;
    BigDecimal amountPaid;
    int order_id;
    String status;


    public AppModelSubscription(int sId, Date startTime, Date endTime, int branchId, BigDecimal amountToPay, BigDecimal amountPaid, int order_id, String status) {
        this.sId = sId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.branchId = branchId;
        this.amountToPay = amountToPay;
        this.amountPaid = amountPaid;
        this.order_id = order_id;
        this.status = status;
    }

    public int getsId() {
        return sId;
    }

    public void setsId(int sId) {
        this.sId = sId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public BigDecimal getAmountToPay() {
        return amountToPay;
    }

    public void setAmountToPay(BigDecimal amountToPay) {
        this.amountToPay = amountToPay;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public int getOrder_id() {
        return order_id;
    }

    public void setOrder_id(int order_id) {
        this.order_id = order_id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
