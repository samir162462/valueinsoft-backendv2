/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;

public class InventoryTransaction {
    int transId;
    int productId;
    String userName;
    int supplierId;
    String transactionType;
    int numItems;
    int transTotal;
    String payType;
    Timestamp time;
    int remainingAmount;

    public InventoryTransaction(int transId, int productId, String userName, int supplierId, String transactionType, int numItems, int transTotal, String payType, Timestamp time, int remainingAmount) {
        this.transId = transId;
        this.productId = productId;
        this.userName = userName;
        this.supplierId = supplierId;
        this.transactionType = transactionType;
        this.numItems = numItems;
        this.transTotal = transTotal;
        this.payType = payType;
        this.time = time;
        this.remainingAmount = remainingAmount;
    }

    public int getTransId() {
        return transId;
    }

    public void setTransId(int transId) {
        this.transId = transId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public int getNumItems() {
        return numItems;
    }

    public void setNumItems(int numItems) {
        this.numItems = numItems;
    }

    public int getTransTotal() {
        return transTotal;
    }

    public void setTransTotal(int transTotal) {
        this.transTotal = transTotal;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    public Timestamp getTime() {
        return time;
    }

    public void setTime(Timestamp time) {
        this.time = time;
    }

    public int getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(int remainingAmount) {
        this.remainingAmount = remainingAmount;
    }
}


