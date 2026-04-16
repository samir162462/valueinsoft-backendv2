package com.example.valueinsoftbackend.Model.Request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class CompanyAnalysisUpdateRequest {

    @Positive(message = "branchId must be positive")
    private int branchId;

    @Positive(message = "companyId must be positive")
    private int companyId;

    @PositiveOrZero(message = "sales must be zero or greater")
    private int sales;

    @PositiveOrZero(message = "income must be zero or greater")
    private int income;

    @PositiveOrZero(message = "clientIn must be zero or greater")
    private int clientIn;

    @PositiveOrZero(message = "invShortage must be zero or greater")
    private int invShortage;

    @PositiveOrZero(message = "discountByUser must be zero or greater")
    private int discountByUser;

    @PositiveOrZero(message = "damagedProducts must be zero or greater")
    private int damagedProducts;

    @PositiveOrZero(message = "returnPurchases must be zero or greater")
    private int returnPurchases;

    @PositiveOrZero(message = "shiftEndsEarly must be zero or greater")
    private int shiftEndsEarly;

    public CompanyAnalysisUpdateRequest() {
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

    public int getSales() {
        return sales;
    }

    public void setSales(int sales) {
        this.sales = sales;
    }

    public int getIncome() {
        return income;
    }

    public void setIncome(int income) {
        this.income = income;
    }

    public int getClientIn() {
        return clientIn;
    }

    public void setClientIn(int clientIn) {
        this.clientIn = clientIn;
    }

    public int getInvShortage() {
        return invShortage;
    }

    public void setInvShortage(int invShortage) {
        this.invShortage = invShortage;
    }

    public int getDiscountByUser() {
        return discountByUser;
    }

    public void setDiscountByUser(int discountByUser) {
        this.discountByUser = discountByUser;
    }

    public int getDamagedProducts() {
        return damagedProducts;
    }

    public void setDamagedProducts(int damagedProducts) {
        this.damagedProducts = damagedProducts;
    }

    public int getReturnPurchases() {
        return returnPurchases;
    }

    public void setReturnPurchases(int returnPurchases) {
        this.returnPurchases = returnPurchases;
    }

    public int getShiftEndsEarly() {
        return shiftEndsEarly;
    }

    public void setShiftEndsEarly(int shiftEndsEarly) {
        this.shiftEndsEarly = shiftEndsEarly;
    }
}
