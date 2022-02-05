/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;

public class DamagedItem {
    int DId;
    int productId;
    String productName;
    Timestamp time;
    String reason;
    String damagedBy;
    String cashierUser;
    int amountTP;
    boolean paid;
    int branchId;
    int quantity;

    public DamagedItem(int DId, int productId, String productName, Timestamp time, String reason, String damagedBy, String cashierUser, int amountTP, boolean paid, int branchId, int quantity) {
        this.DId = DId;
        this.productId = productId;
        this.productName = productName;
        this.time = time;
        this.reason = reason;
        this.damagedBy = damagedBy;
        this.cashierUser = cashierUser;
        this.amountTP = amountTP;
        this.paid = paid;
        this.branchId = branchId;
        this.quantity = quantity;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getDId() {
        return DId;
    }

    public void setDId(int DId) {
        this.DId = DId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Timestamp getTime() {
        return time;
    }

    public void setTime(Timestamp time) {
        this.time = time;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDamagedBy() {
        return damagedBy;
    }

    public void setDamagedBy(String damagedBy) {
        this.damagedBy = damagedBy;
    }

    public String getCashierUser() {
        return cashierUser;
    }

    public void setCashierUser(String cashierUser) {
        this.cashierUser = cashierUser;
    }

    public int getAmountTP() {
        return amountTP;
    }

    public void setAmountTP(int amountTP) {
        this.amountTP = amountTP;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }
}
