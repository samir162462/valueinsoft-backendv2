package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public class OrderPeriodRequest {

    @Positive(message = "branchId must be greater than zero")
    private int branchId;

    @NotBlank(message = "startTime is required")
    private String startTime;

    @NotBlank(message = "endTime is required")
    private String endTime;

    public OrderPeriodRequest() {
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}
