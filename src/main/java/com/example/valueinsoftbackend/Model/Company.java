package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;
import java.util.ArrayList;

public class Company {
    int companyId ;
    String companyName;
    Timestamp establishedTime;
    String plan;
    int establishPrice;
    int ownerId;
    String currency;
    String comImg;
    ArrayList<Branch> branchList;

    public Company(int companyId, String companyName, Timestamp establishedTime, String plan, int establishPrice, String currency, String comImg, ArrayList<Branch> branchList) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.establishedTime = establishedTime;
        this.plan = plan;
        this.establishPrice = establishPrice;
        this.currency = currency;
        this.comImg = comImg;
        this.branchList = branchList;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getComImg() {
        return comImg;
    }

    public void setComImg(String comImg) {
        this.comImg = comImg;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Timestamp getEstablishedTime() {
        return establishedTime;
    }

    public void setEstablishedTime(Timestamp establishedTime) {
        this.establishedTime = establishedTime;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        plan = plan;
    }

    public int getEstablishPrice() {
        return establishPrice;
    }

    public void setEstablishPrice(int establishPrice) {
        this.establishPrice = establishPrice;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }

    public ArrayList<Branch> getBranchList() {
        return branchList;
    }

    public void setBranchList(ArrayList<Branch> branchList) {
        this.branchList = branchList;
    }

    @Override
    public String toString() {
        return "Company{" +
                "companyId=" + companyId +
                ", CompanyName='" + companyName + '\'' +
                ", EstablishedTime=" + establishedTime +
                ", Plan='" + plan + '\'' +
                ", EstablishPrice=" + establishPrice +
                ", ownerId=" + ownerId +
                ", currency='" + currency + '\'' +
                ", comImg='" + comImg + '\'' +
                ", branchList=" + branchList +
                '}';
    }
}
