/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Sales;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class SupplierReceipt {

    int srId;
    int transId;
    BigDecimal amountPaid;
    BigDecimal remainingAmount;
    Timestamp receiptTime;
    String userRecived;
    int supplierId;
    String type;
    int branchId;

    public SupplierReceipt(int srId, int transId, BigDecimal amountPaid, BigDecimal remainingAmount, Timestamp receiptTime, String userRecived, int supplierId, String type, int branchId) {
        this.srId = srId;
        this.transId = transId;
        this.amountPaid = amountPaid;
        this.remainingAmount = remainingAmount;
        this.receiptTime = receiptTime;
        this.userRecived = userRecived;
        this.supplierId = supplierId;
        this.type = type;
        this.branchId = branchId;
    }

    public int getSrId() {
        return srId;
    }

    public void setSrId(int srId) {
        this.srId = srId;
    }

    public int getTransId() {
        return transId;
    }

    public void setTransId(int transId) {
        this.transId = transId;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public Timestamp getReceiptTime() {
        return receiptTime;
    }

    public void setReceiptTime(Timestamp receiptTime) {
        this.receiptTime = receiptTime;
    }

    public String getUserRecived() {
        return userRecived;
    }

    public void setUserRecived(String userRecived) {
        this.userRecived = userRecived;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }
}
