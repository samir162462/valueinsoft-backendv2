package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.Positive;

public class ShiftOrdersRequest {

    @Positive(message = "branchId must be greater than zero")
    private int branchId;

    @Positive(message = "spId must be greater than zero")
    private int spId;

    public ShiftOrdersRequest() {
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public int getSpId() {
        return spId;
    }

    public void setSpId(int spId) {
        this.spId = spId;
    }
}
