/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

public class MainMajor {
    int mId;
    String majorName;
    String appType;

    public MainMajor(int mId, String majorName, String appType) {
        this.mId = mId;
        this.majorName = majorName;
        this.appType = appType;
    }

    public int getmId() {
        return mId;
    }

    public void setmId(int mId) {
        this.mId = mId;
    }

    public String getMajorName() {
        return majorName;
    }

    public void setMajorName(String majorName) {
        this.majorName = majorName;
    }

    public String getAppType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType;
    }
}
