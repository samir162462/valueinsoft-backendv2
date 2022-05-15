/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Util;

public class ProductUtilNames {
    String productName ;
    String companyName;
    String type;
    String major;


    public ProductUtilNames(String productName, String companyName, String type, String major) {
        this.productName = productName;
        this.companyName = companyName;
        this.type = type;
        this.major = major;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }
}
