package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class BounceBackOrderRequest {

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Positive(message = "odId must be positive")
    private int odId;

    @Min(value = 1, message = "toWho must be 1 or 2")
    @Max(value = 2, message = "toWho must be 1 or 2")
    private int toWho;

    @Size(max = 240, message = "reason must be 240 characters or fewer")
    private String reason;

    public BounceBackOrderRequest() {
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public int getOdId() {
        return odId;
    }

    public void setOdId(int odId) {
        this.odId = odId;
    }

    public int getToWho() {
        return toWho;
    }

    public void setToWho(int toWho) {
        this.toWho = toWho;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
