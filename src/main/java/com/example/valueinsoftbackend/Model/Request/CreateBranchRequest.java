package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public class CreateBranchRequest {

    @JsonAlias("CompanyId")
    @Positive(message = "CompanyId must be positive")
    private int companyId;

    @NotBlank(message = "branchName is required")
    private String branchName;

    @NotBlank(message = "branchLocation is required")
    private String branchLocation;

    public CreateBranchRequest() {
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getBranchLocation() {
        return branchLocation;
    }

    public void setBranchLocation(String branchLocation) {
        this.branchLocation = branchLocation;
    }
}
