package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

public class CreateClientRequest {

    @PositiveOrZero(message = "clientId must be zero or greater")
    private int clientId;

    @NotBlank(message = "clientName is required")
    @Size(max = 45, message = "clientName must be 45 characters or fewer")
    private String clientName;

    @NotBlank(message = "clientPhone is required")
    @Size(max = 16, message = "clientPhone must be 16 characters or fewer")
    private String clientPhone;

    @NotBlank(message = "gender is required")
    @Size(max = 20, message = "gender must be 20 characters or fewer")
    private String gender;

    @JsonAlias({"desc", "description"})
    @Size(max = 255, message = "desc must be 255 characters or fewer")
    private String desc;

    @Positive(message = "branchId must be greater than zero")
    private int branchId;

    public CreateClientRequest() {
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public void setClientPhone(String clientPhone) {
        this.clientPhone = clientPhone;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
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
}
