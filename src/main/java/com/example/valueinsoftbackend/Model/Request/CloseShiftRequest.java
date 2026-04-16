package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.PositiveOrZero;

public class CloseShiftRequest {

    @PositiveOrZero(message = "countedCash must be zero or greater")
    private double countedCash;

    private String varianceReason;
    private String closeNote;

    public CloseShiftRequest() {}

    public double getCountedCash() { return countedCash; }
    public void setCountedCash(double countedCash) { this.countedCash = countedCash; }

    public String getVarianceReason() { return varianceReason; }
    public void setVarianceReason(String varianceReason) { this.varianceReason = varianceReason; }

    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String closeNote) { this.closeNote = closeNote; }
}
