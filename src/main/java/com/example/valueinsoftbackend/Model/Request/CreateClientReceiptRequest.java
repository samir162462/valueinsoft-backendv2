package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import com.example.valueinsoftbackend.Model.OpenItems.OpenItemsWriteModels;

public class CreateClientReceiptRequest {

    @NotBlank(message = "type is required")
    private String type;

    @NotNull(message = "amount is required")
    private BigDecimal amount;

    @NotBlank(message = "userName is required")
    private String userName;

    @Positive(message = "clientId must be greater than zero")
    private int clientId;

    @Positive(message = "branchId must be greater than zero")
    private int branchId;

    private String currencyCode;
    private String idempotencyKey;
    private List<OpenItemsWriteModels.AllocationTarget> allocations;

    public CreateClientReceiptRequest() {
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
