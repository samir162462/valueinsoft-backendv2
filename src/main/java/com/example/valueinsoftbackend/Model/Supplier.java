/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

public class Supplier {

    int supplierId;
    String supplierName;
    String supplierPhone1;
    String supplierphone2;
    String suplierLocation;

    public Supplier(int supplierId, String supplierName, String supplierPhone1, String supplierphone2, String suplierLocation) {
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.supplierPhone1 = supplierPhone1;
        this.supplierphone2 = supplierphone2;
        this.suplierLocation = suplierLocation;
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

    public String getSupplierphone2() {
        return supplierphone2;
    }

    public void setSupplierphone2(String supplierphone2) {
        this.supplierphone2 = supplierphone2;
    }

    public String getSuplierLocation() {
        return suplierLocation;
    }

    public void setSuplierLocation(String suplierLocation) {
        this.suplierLocation = suplierLocation;
    }
}
