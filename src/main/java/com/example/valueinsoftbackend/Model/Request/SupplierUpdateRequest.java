package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Size;

public class SupplierUpdateRequest {

    private Integer supplierId;

    @Size(max = 60, message = "supplierName must be 60 characters or fewer")
    private String supplierName;

    @Size(max = 16, message = "supplierPhone1 must be 16 characters or fewer")
    private String supplierPhone1;

    @Size(max = 16, message = "supplierPhone2 must be 16 characters or fewer")
    private String supplierPhone2;

    @Size(max = 60, message = "suplierLocation must be 60 characters or fewer")
    private String suplierLocation;

    @Size(max = 60, message = "supplierLocation must be 60 characters or fewer")
    private String supplierLocation;

    @Size(max = 30, message = "suplierMajor must be 30 characters or fewer")
    private String suplierMajor;

    @Size(max = 30, message = "supplierMajor must be 30 characters or fewer")
    private String supplierMajor;

    public SupplierUpdateRequest() {
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Integer supplierId) {
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
        return supplierLocation == null || supplierLocation.isBlank() ? suplierLocation : supplierLocation;
    }

    public void setSuplierLocation(String suplierLocation) {
        this.suplierLocation = suplierLocation;
    }

    public String getSupplierLocation() {
        return getSuplierLocation();
    }

    public void setSupplierLocation(String supplierLocation) {
        this.supplierLocation = supplierLocation;
    }

    public String getSuplierMajor() {
        return supplierMajor == null || supplierMajor.isBlank() ? suplierMajor : supplierMajor;
    }

    public void setSuplierMajor(String suplierMajor) {
        this.suplierMajor = suplierMajor;
    }

    public String getSupplierMajor() {
        return getSuplierMajor();
    }

    public void setSupplierMajor(String supplierMajor) {
        this.supplierMajor = supplierMajor;
    }
}
