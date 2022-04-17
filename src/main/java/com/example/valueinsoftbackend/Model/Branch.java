package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;

public class Branch {
    int branchID;
    int branchOfCompanyId;
    String branchName;
    String branchLocation;
    Timestamp branchEstTime;


    public Branch(int branchID, int branchOfCompanyId, String branchName, String branchLocation, Timestamp branchEstTime) {
        this.branchID = branchID;
        this.branchOfCompanyId = branchOfCompanyId;
        this.branchName = branchName;
        this.branchLocation = branchLocation;
        this.branchEstTime = branchEstTime;
    }


    public int getBranchID() {
        return branchID;
    }

    public void setBranchID(int branchID) {
        this.branchID = branchID;
    }

    public int getBranchOfCompanyId() {
        return branchOfCompanyId;
    }

    public void setBranchOfCompanyId(int branchOfCompanyId) {
        this.branchOfCompanyId = branchOfCompanyId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getBranchLocation() {
        return branchLocation;
    }

    public void setBranchLocation(String branchLocation) {
        this.branchLocation = branchLocation;
    }

    public Timestamp getBranchEstTime() {
        return branchEstTime;
    }

    public void setBranchEstTime(Timestamp branchEstTime) {
        this.branchEstTime = branchEstTime;
    }

    @Override
    public String toString() {
        return "Branch{" +
                "branchID=" + branchID +
                ", branchOfCompanyId=" + branchOfCompanyId +
                ", branchName='" + branchName + '\'' +
                ", branchLocation='" + branchLocation + '\'' +
                ", branchEstTime=" + branchEstTime +
                '}';
    }
}
