package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public class DashboardSummaryRequest implements Serializable {

    @NotNull(message = "branchId is required")
    private Integer branchId;

    @NotNull(message = "date is required")
    private String date; // Expected format: YYYY-MM-DD

    private String period; // e.g., "TODAY", "WEEK", "MONTH"
    
    public DashboardSummaryRequest() {}

    public Integer getBranchId() {
        return branchId;
    }

    public void setBranchId(Integer branchId) {
        this.branchId = branchId;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }
}
