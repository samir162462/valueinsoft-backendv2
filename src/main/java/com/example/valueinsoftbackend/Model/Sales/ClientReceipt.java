/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Sales;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class ClientReceipt {
    int crId;
    String type;
    BigDecimal amount;
    Timestamp time;
    String userName;
    int clientId;
    int branchId;

    public ClientReceipt(int crId, String type, BigDecimal amount, Timestamp time, String userName, int clientId, int branchId) {
        this.crId = crId;
        this.type = type;
        this.amount = amount;
        this.time = time;
        this.userName = userName;
        this.clientId = clientId;
        this.branchId = branchId;
    }

    public int getCrId() {
        return crId;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public void setCrId(int crId) {
        this.crId = crId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Timestamp getTime() {
        return time;
    }

    public void setTime(Timestamp time) {
        this.time = time;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    @Override
    public String toString() {
        return "ClientReceipt{" +
                "crId=" + crId +
                ", type='" + type + '\'' +
                ", amount=" + amount +
                ", time=" + time +
                ", userName='" + userName + '\'' +
                ", clientId=" + clientId +
                ", branchId=" + branchId +
                '}';
    }
}
