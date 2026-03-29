package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;

public class BounceBackOrderRequest {

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Positive(message = "odId must be positive")
    private int odId;

    @Min(value = 1, message = "toWho must be 1 or 2")
    @Max(value = 2, message = "toWho must be 1 or 2")
    private int toWho;

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
}
