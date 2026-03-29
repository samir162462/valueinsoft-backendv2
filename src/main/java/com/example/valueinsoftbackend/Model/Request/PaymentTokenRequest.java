package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public class PaymentTokenRequest {

    @JsonProperty("amount_cents")
    @Positive(message = "amount_cents must be positive")
    private long amountCents;

    @JsonProperty("order_id")
    @Positive(message = "order_id must be positive")
    private long orderId;

    @NotBlank(message = "currency is required")
    private String currency;

    @Positive(message = "companyId must be positive")
    private int companyId;

    @Positive(message = "branchId must be positive")
    private int branchId;

    public PaymentTokenRequest() {
    }

    public long getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(long amountCents) {
        this.amountCents = amountCents;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }
}
