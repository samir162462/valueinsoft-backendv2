package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.Positive;

public class CurrentShiftRequest {

    @Positive(message = "branchId must be greater than zero")
    private int branchId;

    private boolean getDetails;

    public CurrentShiftRequest() {
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public boolean isGetDetails() {
        return getDetails;
    }

    public void setGetDetails(boolean getDetails) {
        this.getDetails = getDetails;
    }
}
