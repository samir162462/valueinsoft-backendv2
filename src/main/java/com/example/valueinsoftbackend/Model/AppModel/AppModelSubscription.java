/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.AppModel;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

public class AppModelSubscription {

    int sId;
    Date startTime;
    Date endTime;
    int branchId;
    BigDecimal amountToPay;
    BigDecimal amountPaid;
    int order_id;
    String status;
    long billingInvoiceId;
    String createdBy;
    Timestamp createdAt;
    Timestamp updatedAt;


    public AppModelSubscription(int sId, Date startTime, Date endTime, int branchId, BigDecimal amountToPay, BigDecimal amountPaid, int order_id, String status) {
        this(sId, startTime, endTime, branchId, amountToPay, amountPaid, order_id, status, 0L);
    }

    public AppModelSubscription(int sId, Date startTime, Date endTime, int branchId, BigDecimal amountToPay, BigDecimal amountPaid, int order_id, String status, long billingInvoiceId) {
        this(sId, startTime, endTime, branchId, amountToPay, amountPaid, order_id, status, billingInvoiceId, null, null, null);
    }

    public AppModelSubscription(int sId, Date startTime, Date endTime, int branchId, BigDecimal amountToPay, BigDecimal amountPaid, int order_id, String status, long billingInvoiceId, String createdBy, Timestamp createdAt, Timestamp updatedAt) {
        this.sId = sId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.branchId = branchId;
        this.amountToPay = amountToPay;
        this.amountPaid = amountPaid;
        this.order_id = order_id;
        this.status = status;
        this.billingInvoiceId = billingInvoiceId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public long getBillingInvoiceId() {
        return billingInvoiceId;
    }

    public void setBillingInvoiceId(long billingInvoiceId) {
        this.billingInvoiceId = billingInvoiceId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
