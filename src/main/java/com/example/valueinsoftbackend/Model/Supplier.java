/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

public class Supplier {

    int supplierId;
    String supplierName;
    String supplierPhone1;
    String supplierPhone2;
    String suplierLocation;
    String suplierMajor;

    public Supplier(int supplierId, String supplierName, String supplierPhone1, String supplierPhone2, String suplierLocation, String suplierMajor) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.supplierPhone1 = supplierPhone1;
        this.supplierPhone2 = supplierPhone2;
        this.suplierLocation = suplierLocation;
        this.suplierMajor = suplierMajor;
    }

    public String getSuplierMajor() {
        return suplierMajor;
    }

    public void setSuplierMajor(String suplierMajor) {
        this.suplierMajor = suplierMajor;
    }

    public int getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(int supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public String getSupplierPhone1() {
        return supplierPhone1;
    }

    public void setSupplierPhone1(String supplierPhone1) {
        this.supplierPhone1 = supplierPhone1;
    }

    public String getSupplierPhone2() {
        return supplierPhone2;
    }

    public void setSupplierPhone2(String supplierPhone2) {
        this.supplierPhone2 = supplierPhone2;
    }

    public String getSuplierLocation() {
        return suplierLocation;
    }

    public void setSuplierLocation(String suplierLocation) {
        this.suplierLocation = suplierLocation;
    }
}
