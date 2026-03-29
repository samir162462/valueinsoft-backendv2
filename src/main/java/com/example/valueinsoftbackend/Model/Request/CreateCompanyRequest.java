package com.example.valueinsoftbackend.Model.Request;

import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;

public class CreateCompanyRequest {

    @NotBlank(message = "companyName is required")
    private String companyName;

    @NotBlank(message = "branchName is required")
    private String branchName;

    @NotBlank(message = "plan is required")
    private String plan;

    @JsonAlias("EstablishPrice")
    @PositiveOrZero(message = "EstablishPrice must be zero or greater")
    private int establishPrice;

    @NotBlank(message = "ownerName is required")
    private String ownerName;

    private String comImg;

    @NotBlank(message = "currency is required")
    private String currency;

    private String branchMajor;

    public CreateCompanyRequest() {
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public int getEstablishPrice() {
        return establishPrice;
    }

    public void setEstablishPrice(int establishPrice) {
        this.establishPrice = establishPrice;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getComImg() {
        return comImg;
    }

    public void setComImg(String comImg) {
        this.comImg = comImg;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getBranchMajor() {
        return branchMajor;
    }

    public void setBranchMajor(String branchMajor) {
        this.branchMajor = branchMajor;
    }
}
