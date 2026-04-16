package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

public class OpenShiftRequest {

    @Positive(message = "branchId must be positive")
    private int branchId;

    @PositiveOrZero(message = "openingFloat must be zero or greater")
    private double openingFloat;

    private String registerCode;

    public OpenShiftRequest() {}

    public int getBranchId() { return branchId; }
    public void setBranchId(int branchId) { this.branchId = branchId; }

    public double getOpeningFloat() { return openingFloat; }
    public void setOpeningFloat(double openingFloat) { this.openingFloat = openingFloat; }

    public String getRegisterCode() { return registerCode; }
    public void setRegisterCode(String registerCode) { this.registerCode = registerCode; }
}
