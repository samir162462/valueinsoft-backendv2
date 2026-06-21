package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

public class CompanyDashboardSummaryRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "date is required")
    private String date;

    private String period;

    public CompanyDashboardSummaryRequest() {
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
