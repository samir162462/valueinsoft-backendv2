package com.example.valueinsoftbackend.Model.Request;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import com.example.valueinsoftbackend.Model.Slots.FixAreaPart;

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

    private String imei;
    private String deviceCondition;
    private String accessories;

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

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getDeviceCondition() {
        return deviceCondition;
    }

    public void setDeviceCondition(String deviceCondition) {
        this.deviceCondition = deviceCondition;
    }

    public String getAccessories() {
        return accessories;
    }

    public void setAccessories(String accessories) {
        this.accessories = accessories;
    }

    private List<FixAreaPart> usedParts;

    public List<FixAreaPart> getUsedParts() {
        return usedParts;
    }

    public void setUsedParts(List<FixAreaPart> usedParts) {
        this.usedParts = usedParts;
    }
}
