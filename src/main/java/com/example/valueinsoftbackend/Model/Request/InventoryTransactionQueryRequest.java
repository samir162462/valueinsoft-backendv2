package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class InventoryTransactionQueryRequest {

    @Positive(message = "branchId must be positive")
    private int branchId;

    @NotBlank(message = "startTime is required")
    private String startTime;

    @NotBlank(message = "endTime is required")
    private String endTime;

    @Positive(message = "companyId must be positive")
    private int companyId;

    public InventoryTransactionQueryRequest() {
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

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }
}
