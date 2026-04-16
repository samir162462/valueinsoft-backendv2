package com.example.valueinsoftbackend.Model.Request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class FixAreaSlotCreateRequest {

    @PositiveOrZero(message = "faId must be zero or greater")
    private int faId;

    @Positive(message = "fixSlot must be positive")
    private int fixSlot;

    @Positive(message = "clientId must be positive")
    private int clientId;

    @NotBlank(message = "dateIn is required")
    private String dateIn;

    @NotBlank(message = "dateFinished is required")
    private String dateFinished;

    @NotBlank(message = "phoneName is required")
    private String phoneName;

    @NotBlank(message = "problem is required")
    private String problem;

    private boolean show;

    @NotBlank(message = "userName_Recived is required")
    private String userName_Recived;

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

    public FixAreaSlotCreateRequest() {
    }

    public int getFaId() {
        return faId;
    }

    public void setFaId(int faId) {
        this.faId = faId;
    }

    public int getFixSlot() {
        return fixSlot;
    }

    public void setFixSlot(int fixSlot) {
        this.fixSlot = fixSlot;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getDateIn() {
        return dateIn;
    }

    public void setDateIn(String dateIn) {
        this.dateIn = dateIn;
    }

    public String getDateFinished() {
        return dateFinished;
    }

    public void setDateFinished(String dateFinished) {
        this.dateFinished = dateFinished;
    }

    public String getPhoneName() {
        return phoneName;
    }

    public void setPhoneName(String phoneName) {
        this.phoneName = phoneName;
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

    public String getUserName_Recived() {
        return userName_Recived;
    }

    public void setUserName_Recived(String userName_Recived) {
        this.userName_Recived = userName_Recived;
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
}
