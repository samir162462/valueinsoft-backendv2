/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.DataVisualizationModels;

public class DVSalesYearly {
    String month ;
    int sum;
    int numberOfMonth;
    int income;

    public DVSalesYearly(String month, int sum, int numberOfMonth, int income) {
        this.month = month;
        this.sum = sum;
        this.numberOfMonth = numberOfMonth;
        this.income = income;
    }

    public int getIncome() {
        return income;
    }

    public void setIncome(int income) {
        this.income = income;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public int getSum() {
        return sum;
    }

    public void setSum(int sum) {
        this.sum = sum;
    }

    public int getNumberOfMonth() {
        return numberOfMonth;
    }

    public void setNumberOfMonth(int numberOfMonth) {
        this.numberOfMonth = numberOfMonth;
    }
}
