package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class CreateDamagedItemRequest {

    @PositiveOrZero(message = "DId must be zero or greater")
    private int DId;

    @Positive(message = "productId must be positive")
    private int productId;

    @NotBlank(message = "productName is required")
    private String productName;

    @NotBlank(message = "time is required")
    private String time;

    @NotBlank(message = "reason is required")
    private String reason;

    @NotBlank(message = "damagedBy is required")
    private String damagedBy;

    @NotBlank(message = "cashierUser is required")
    private String cashierUser;

    @PositiveOrZero(message = "amountTP must be zero or greater")
    private int amountTP;

    private boolean paid;

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Positive(message = "quantity must be positive")
    private int quantity;

    public CreateDamagedItemRequest() {
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

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
