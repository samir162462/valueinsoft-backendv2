/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.DataVisualizationModels;

import java.util.Date;

public class CompanyAnalysis {
   int sales;
   int income;
   int clientsIn;
   int invShortage;
   int discountByUsers;
   int damagedProducts;
   int returnPurchases;
   int shiftEndsEarly;
   Date date;
   int countDays;


    public CompanyAnalysis(int sales, int income, int clientsIn, int invShortage, int discountByUsers, int damagedProducts, int returnPurchases, int shiftEndsEarly, Date date, int countDays) {
        this.sales = sales;
        this.income = income;
        this.clientsIn = clientsIn;
        this.invShortage = invShortage;
        this.discountByUsers = discountByUsers;
        this.damagedProducts = damagedProducts;
        this.returnPurchases = returnPurchases;
        this.shiftEndsEarly = shiftEndsEarly;
        this.date = date;
        this.countDays = countDays;
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

    public int getClientsIn() {
        return clientsIn;
    }

    public void setClientsIn(int clientsIn) {
        this.clientsIn = clientsIn;
    }

    public int getInvShortage() {
        return invShortage;
    }

    public void setInvShortage(int invShortage) {
        this.invShortage = invShortage;
    }

    public int getDiscountByUsers() {
        return discountByUsers;
    }

    public void setDiscountByUsers(int discountByUsers) {
        this.discountByUsers = discountByUsers;
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

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getCountDays() {
        return countDays;
    }

    public void setCountDays(int countDays) {
        this.countDays = countDays;
    }
}
