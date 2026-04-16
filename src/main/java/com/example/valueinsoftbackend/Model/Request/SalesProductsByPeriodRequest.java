package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class SalesProductsByPeriodRequest {

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Positive(message = "companyId must be positive")
    private int companyId;

    @NotBlank(message = "startTime is required")
    private String startTime;

    @NotBlank(message = "endTime is required")
    private String endTime;

    public SalesProductsByPeriodRequest() {
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
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
