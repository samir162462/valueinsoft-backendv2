/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Sales;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

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
    String postingStatus;
    UUID postingRequestId;
    UUID journalId;
    String postingFailureReason;

    public SupplierReceipt(int srId, int transId, BigDecimal amountPaid, BigDecimal remainingAmount, Timestamp receiptTime, String userRecived, int supplierId, String type, int branchId) {
        this(srId, transId, amountPaid, remainingAmount, receiptTime, userRecived, supplierId, type, branchId, null, null, null, null);
    }

    public SupplierReceipt(int srId,
                           int transId,
                           BigDecimal amountPaid,
                           BigDecimal remainingAmount,
                           Timestamp receiptTime,
                           String userRecived,
                           int supplierId,
                           String type,
                           int branchId,
                           String postingStatus,
                           UUID postingRequestId,
                           UUID journalId,
                           String postingFailureReason) {
        this.srId = srId;
        this.transId = transId;
        this.amountPaid = amountPaid;
        this.remainingAmount = remainingAmount;
        this.receiptTime = receiptTime;
        this.userRecived = userRecived;
        this.supplierId = supplierId;
        this.type = type;
        this.branchId = branchId;
        this.postingStatus = postingStatus;
        this.postingRequestId = postingRequestId;
        this.journalId = journalId;
        this.postingFailureReason = postingFailureReason;
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
}
