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
    ArrayList<Branch> branchList;

    public Company(int companyId, String companyName, Timestamp establishedTime, String plan, int establishPrice, ArrayList<Branch> branchList) {
        this.companyId = companyId;
        CompanyName = companyName;
        EstablishedTime = establishedTime;
        Plan = plan;
        EstablishPrice = establishPrice;
        this.branchList = branchList;
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
}
