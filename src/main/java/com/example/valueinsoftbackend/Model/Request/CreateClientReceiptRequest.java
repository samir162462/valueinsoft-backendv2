package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

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
}
