package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class CompanyAnalysisRequest {

    @PositiveOrZero(message = "branchId must be zero or greater")
    private int branchId;

    @Positive(message = "companyId must be positive")
    private int companyId;

    public CompanyAnalysisRequest() {
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
}
