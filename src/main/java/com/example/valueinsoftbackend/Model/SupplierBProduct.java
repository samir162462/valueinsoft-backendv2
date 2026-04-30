/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;
import java.util.UUID;

public class SupplierBProduct {

    int sBPId;
    int productId;
    int supplierId;
    int quantity;
    int cost;
    String userName;
    int sPaid;
    Timestamp time;
    String desc;
    int orderDetailsId;
    String postingStatus;
    UUID postingRequestId;
    UUID journalId;
    String postingFailureReason;


    public SupplierBProduct(int sBPId, int productId, int quantity, int cost, String userName, int sPaid, Timestamp time, String desc, int orderDetailsId) {
        this(sBPId, productId, 0, quantity, cost, userName, sPaid, time, desc, orderDetailsId, null, null, null, null);
    }

    public SupplierBProduct(int sBPId, int productId, int quantity, int cost, String userName, int sPaid, Timestamp time, String desc, int orderDetailsId, String postingStatus, UUID postingRequestId, UUID journalId, String postingFailureReason) {
        this(sBPId, productId, 0, quantity, cost, userName, sPaid, time, desc, orderDetailsId, postingStatus, postingRequestId, journalId, postingFailureReason);
    }

    public SupplierBProduct(int sBPId, int productId, int supplierId, int quantity, int cost, String userName, int sPaid, Timestamp time, String desc, int orderDetailsId, String postingStatus, UUID postingRequestId, UUID journalId, String postingFailureReason) {
        this.sBPId = sBPId;
        this.productId = productId;
        this.supplierId = supplierId;
        this.quantity = quantity;
        this.cost = cost;
        this.userName = userName;
        this.sPaid = sPaid;
        this.time = time;
        this.desc = desc;
        this.orderDetailsId = orderDetailsId;
        this.postingStatus = postingStatus;
        this.postingRequestId = postingRequestId;
        this.journalId = journalId;
        this.postingFailureReason = postingFailureReason;
    }

    public int getsBPId() {
        return sBPId;
    }

    public void setsBPId(int sBPId) {
        this.sBPId = sBPId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int getOrderDetailsId() {
        return orderDetailsId;
    }

    public void setOrderDetailsId(int orderDetailsId) {
        this.orderDetailsId = orderDetailsId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getsPaid() {
        return sPaid;
    }

    public void setsPaid(int sPaid) {
        this.sPaid = sPaid;
    }

    public Timestamp getTime() {
        return time;
    }

    public void setTime(Timestamp time) {
        this.time = time;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getPostingStatus() {
        return postingStatus;
    }

    public void setPostingStatus(String postingStatus) {
        this.postingStatus = postingStatus;
    }

    public UUID getPostingRequestId() {
        return postingRequestId;
    }

    public void setPostingRequestId(UUID postingRequestId) {
        this.postingRequestId = postingRequestId;
    }

    public UUID getJournalId() {
        return journalId;
    }

    public void setJournalId(UUID journalId) {
        this.journalId = journalId;
    }

    public String getPostingFailureReason() {
        return postingFailureReason;
    }

    public void setPostingFailureReason(String postingFailureReason) {
        this.postingFailureReason = postingFailureReason;
    }

    @Override
    public String toString() {
        return "SupplierBProduct{" +
                "sBPId=" + sBPId +
                ", productId=" + productId +
                ", supplierId=" + supplierId +
                ", quantity=" + quantity +
                ", cost=" + cost +
                ", userName='" + userName + '\'' +
                ", sPaid=" + sPaid +
                ", time=" + time +
                ", desc='" + desc + '\'' +
                ", orderDetailsId=" + orderDetailsId +
                ", postingStatus='" + postingStatus + '\'' +
                ", postingRequestId=" + postingRequestId +
                ", journalId=" + journalId +
                ", postingFailureReason='" + postingFailureReason + '\'' +
                '}';
    }
}

