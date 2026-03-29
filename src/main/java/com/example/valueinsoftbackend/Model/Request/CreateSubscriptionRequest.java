package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonFormat;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public class CreateSubscriptionRequest {

    @NotNull(message = "startTime is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startTime;

    @NotNull(message = "endTime is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endTime;

    @Positive(message = "branchId must be positive")
    private int branchId;

    @NotNull(message = "amountToPay is required")
    @DecimalMin(value = "0.01", message = "amountToPay must be greater than zero")
    private BigDecimal amountToPay;

    @DecimalMin(value = "0.00", message = "amountPaid must be zero or greater")
    private BigDecimal amountPaid;

    public CreateSubscriptionRequest() {
    }

    public LocalDate getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDate startTime) {
        this.startTime = startTime;
    }

    public LocalDate getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDate endTime) {
        this.endTime = endTime;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public BigDecimal getAmountToPay() {
        return amountToPay;
    }

    public void setAmountToPay(BigDecimal amountToPay) {
        this.amountToPay = amountToPay;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }
}
