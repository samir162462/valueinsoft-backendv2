package com.example.valueinsoftbackend.Model.Request;

import java.math.BigDecimal;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

public class FixAreaSlotUpdateRequest {

    @Positive(message = "faId must be positive")
    private int faId;

    @NotBlank(message = "dateFinished is required")
    private String dateFinished;

    @NotBlank(message = "problem is required")
    private String problem;

    private boolean show;

    @NotBlank(message = "status is required")
    private String status;

    private String desc;

    @Positive(message = "branchId must be positive")
    private int branchId;

    @PositiveOrZero(message = "fees must be zero or greater")
    private BigDecimal fees;

    public FixAreaSlotUpdateRequest() {
    }

    public int getFaId() {
        return faId;
    }

    public void setFaId(int faId) {
        this.faId = faId;
    }

    public String getDateFinished() {
        return dateFinished;
    }

    public void setDateFinished(String dateFinished) {
        this.dateFinished = dateFinished;
    }

    public String getProblem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem;
    }

    public boolean isShow() {
        return show;
    }

    public void setShow(boolean show) {
        this.show = show;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public void setFees(BigDecimal fees) {
        this.fees = fees;
    }
}
