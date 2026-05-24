package com.example.valueinsoftbackend.Model.Request;

import com.example.valueinsoftbackend.Model.Request.Inventory.SerializedUnitInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CreateInventoryTransactionRequest {

    @Positive(message = "productId must be positive")
    private int productId;

    @NotBlank(message = "userName is required")
    @Size(max = 60, message = "userName must be 60 characters or fewer")
    private String userName;

    @Positive(message = "supplierId must be positive")
    private int supplierId;

    @NotBlank(message = "transactionType is required")
    @Size(max = 30, message = "transactionType must be 30 characters or fewer")
    private String transactionType;

    private int numItems;

    private int transTotal;

    @NotBlank(message = "payType is required")
    @Size(max = 30, message = "payType must be 30 characters or fewer")
    private String payType;

    @NotBlank(message = "time is required")
    private String time;

    @PositiveOrZero(message = "remainingAmount must be zero or greater")
    private int remainingAmount;

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Positive(message = "companyId must be positive")
    private int companyId;

    @Size(max = 100, message = "serializedUnits must contain 100 units or fewer")
    private List<SerializedUnitInput> serializedUnits;

    @Size(max = 160, message = "idempotencyKey must be 160 characters or fewer")
    private String idempotencyKey;

    public CreateInventoryTransactionRequest() {
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

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public int getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(int remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public List<SerializedUnitInput> getSerializedUnits() {
        return serializedUnits;
    }

    public void setSerializedUnits(List<SerializedUnitInput> serializedUnits) {
        this.serializedUnits = serializedUnits;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
