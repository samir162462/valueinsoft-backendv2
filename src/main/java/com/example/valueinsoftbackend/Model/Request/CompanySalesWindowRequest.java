package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public class CompanySalesWindowRequest {

    @Positive(message = "companyId must be positive")
    private int companyId;

    @NotBlank(message = "hours is required")
    private String hours;

    public CompanySalesWindowRequest() {
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public String getHours() {
        return hours;
    }

    public void setHours(String hours) {
        this.hours = hours;
    }
}
