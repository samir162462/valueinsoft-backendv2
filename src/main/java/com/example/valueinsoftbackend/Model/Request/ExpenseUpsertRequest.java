package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.sql.Timestamp;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExpenseUpsertRequest {

    @JsonProperty("eId")
    @PositiveOrZero(message = "eId must be zero or greater")
    private int eId;

    @JsonProperty("type")
    @NotBlank(message = "type is required")
    private String type;

    @JsonProperty("amount")
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    @JsonProperty("time")
    private Timestamp time;

    @JsonProperty("user")
    @NotBlank(message = "user is required")
    private String user;

    @JsonProperty("name")
    @NotBlank(message = "name is required")
    private String name;

    @JsonProperty("period")
    private String period;

    @JsonProperty("expenseAccountId")
    private java.util.UUID expenseAccountId;

    @JsonProperty("paymentAccountId")
    private java.util.UUID paymentAccountId;

    @JsonProperty("nextDueDate")
    private java.time.LocalDate nextDueDate;

    @JsonProperty("isStatic")
    private boolean isStatic;

    public ExpenseUpsertRequest() {
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    public java.time.LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(java.time.LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public int getEId() {
        return eId;
    }

    public void setEId(int eId) {
        this.eId = eId;
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

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public java.util.UUID getExpenseAccountId() {
        return expenseAccountId;
    }

    public void setExpenseAccountId(java.util.UUID expenseAccountId) {
        this.expenseAccountId = expenseAccountId;
    }

    public java.util.UUID getPaymentAccountId() {
        return paymentAccountId;
    }

    public void setPaymentAccountId(java.util.UUID paymentAccountId) {
        this.paymentAccountId = paymentAccountId;
    }
}
