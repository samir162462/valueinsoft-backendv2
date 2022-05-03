/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Slots;

import java.sql.Date;

public class SlotsFixArea {
    int faId;
    int fixSlot;
    int clientId;
    Date dateIn;
    Date dateFinished;
    String phoneName;
    String problem;
    boolean show;
    String userName_Recived;
    String status;
    String desc;
     int branchId;


    public SlotsFixArea(int faId, int fixSlot, int clientId, Date dateIn, Date dateFinished, String phoneName, String problem, boolean show, String userName_Recived, String status, String desc, int branchId) {
        this.faId = faId;
        this.fixSlot = fixSlot;
        this.clientId = clientId;
        this.dateIn = dateIn;
        this.dateFinished = dateFinished;
        this.phoneName = phoneName;
        this.problem = problem;
        this.show = show;
        this.userName_Recived = userName_Recived;
        this.status = status;
        this.desc = desc;
        this.branchId = branchId;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
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

    public Date getDateIn() {
        return dateIn;
    }

    public void setDateIn(Date dateIn) {
        this.dateIn = dateIn;
    }

    public Date getDateFinished() {
        return dateFinished;
    }

    public void setDateFinished(Date dateFinished) {
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

    @Override
    public String toString() {
        return "SlotsFixArea{" +
                "faId=" + faId +
                ", fixSlot=" + fixSlot +
                ", clientId=" + clientId +
                ", dateIn=" + dateIn +
                ", dateFinished=" + dateFinished +
                ", PhoneName='" + phoneName + '\'' +
                ", problem='" + problem + '\'' +
                ", show=" + show +
                ", userName_Recived='" + userName_Recived + '\'' +
                ", status='" + status + '\'' +
                ", desc='" + desc + '\'' +
                '}';
    }
}
