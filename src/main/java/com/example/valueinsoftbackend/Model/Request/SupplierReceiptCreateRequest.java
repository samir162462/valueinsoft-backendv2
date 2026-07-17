package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;

public class SupplierReceiptCreateRequest {

    @Positive(message = "transId must be positive")
    private int transId;

    @DecimalMin(value = "0.01", message = "amountPaid must be greater than zero")
    private BigDecimal amountPaid;

    @DecimalMin(value = "0.00", message = "remainingAmount must be zero or greater")
    private BigDecimal remainingAmount;

    @NotBlank(message = "userRecived is required")
    private String userRecived;

    @Positive(message = "supplierId must be positive")
    private int supplierId;

    @NotBlank(message = "type is required")
    private String type;

    @Positive(message = "branchId must be positive")
    private int branchId;

    private String currencyCode;
    private String idempotencyKey;
    private List<OpenItemsWriteModels.AllocationTarget> allocations;

    public SupplierReceiptCreateRequest() {
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

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public List<OpenItemsWriteModels.AllocationTarget> getAllocations() { return allocations; }
    public void setAllocations(List<OpenItemsWriteModels.AllocationTarget> allocations) { this.allocations = allocations; }
}
