package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.sql.Timestamp;

public class ExpenseUpsertRequest {

    @PositiveOrZero(message = "exId must be zero or greater")
    private int exId;

    @NotBlank(message = "type is required")
    private String type;

    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    private BigDecimal amount;

    private Timestamp time;

    @NotBlank(message = "user is required")
    private String user;

    @NotBlank(message = "name is required")
    private String name;

    public ExpenseUpsertRequest() {
    }

    public int getExId() {
        return exId;
    }

    public void setExId(int exId) {
        this.exId = exId;
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
}
