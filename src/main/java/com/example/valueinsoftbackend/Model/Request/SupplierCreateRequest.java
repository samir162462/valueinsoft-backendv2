package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public class SupplierCreateRequest {

    @NotBlank(message = "supplierName is required")
    @Size(max = 60, message = "supplierName must be 60 characters or fewer")
    private String supplierName;

    @NotBlank(message = "supplierPhone1 is required")
    @Size(max = 16, message = "supplierPhone1 must be 16 characters or fewer")
    private String supplierPhone1;

    @Size(max = 16, message = "supplierPhone2 must be 16 characters or fewer")
    private String supplierPhone2;

    @NotBlank(message = "suplierLocation is required")
    @Size(max = 60, message = "suplierLocation must be 60 characters or fewer")
    private String suplierLocation;

    @NotBlank(message = "suplierMajor is required")
    @Size(max = 30, message = "suplierMajor must be 30 characters or fewer")
    private String suplierMajor;

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Positive(message = "companyId must be positive")
    private int companyId;

    public SupplierCreateRequest() {
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

    public String getSuplierMajor() {
        return suplierMajor;
    }

    public void setSuplierMajor(String suplierMajor) {
        this.suplierMajor = suplierMajor;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public int getCompanyId() {
        return companyId;
    }

    public void setCompanyId(int companyId) {
        this.companyId = companyId;
    }
}
