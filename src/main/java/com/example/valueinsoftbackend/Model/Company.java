package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;
import java.util.ArrayList;

public class Company {
    int companyId ;
    String CompanyName;
    Timestamp EstablishedTime;
    String Plan;
    int EstablishPrice;
    int ownerId;
    String currency;
    String comImg;
    ArrayList<Branch> branchList;

    public Company(int companyId, String companyName, Timestamp establishedTime, String plan, int establishPrice, String currency, String comImg, ArrayList<Branch> branchList) {
        this.companyId = companyId;
        CompanyName = companyName;
        EstablishedTime = establishedTime;
        Plan = plan;
        EstablishPrice = establishPrice;
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
        return CompanyName;
    }

    public void setCompanyName(String companyName) {
        CompanyName = companyName;
    }

    public Timestamp getEstablishedTime() {
        return EstablishedTime;
    }

    public void setEstablishedTime(Timestamp establishedTime) {
        EstablishedTime = establishedTime;
    }

    public String getPlan() {
        return Plan;
    }

    public void setPlan(String plan) {
        Plan = plan;
    }

    public int getEstablishPrice() {
        return EstablishPrice;
    }

    public void setEstablishPrice(int establishPrice) {
        EstablishPrice = establishPrice;
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
                ", CompanyName='" + CompanyName + '\'' +
                ", EstablishedTime=" + EstablishedTime +
                ", Plan='" + Plan + '\'' +
                ", EstablishPrice=" + EstablishPrice +
                ", ownerId=" + ownerId +
                ", currency='" + currency + '\'' +
                ", comImg='" + comImg + '\'' +
                ", branchList=" + branchList +
                '}';
    }
}
