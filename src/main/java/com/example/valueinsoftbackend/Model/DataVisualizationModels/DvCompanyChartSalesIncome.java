/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.DataVisualizationModels;

import java.util.ArrayList;

public class DvCompanyChartSalesIncome {

    int BranchID ;
    ArrayList<Integer> sumTotal;
    ArrayList<Integer> sumIncome;
    ArrayList<String> Dates;

    public DvCompanyChartSalesIncome(int branchID, ArrayList<Integer> sumTotal, ArrayList<Integer> sumIncome, ArrayList<String> dates) {
        BranchID = branchID;
        this.sumTotal = sumTotal;
        this.sumIncome = sumIncome;
        Dates = dates;
    }

    public int getBranchID() {
        return BranchID;
    }

    public void setBranchID(int branchID) {
        BranchID = branchID;
    }

    public ArrayList<Integer> getSumTotal() {
        return sumTotal;
    }

    public void setSumTotal(ArrayList<Integer> sumTotal) {
        this.sumTotal = sumTotal;
    }

    public ArrayList<Integer> getSumIncome() {
        return sumIncome;
    }

    public void setSumIncome(ArrayList<Integer> sumIncome) {
        this.sumIncome = sumIncome;
    }

    public ArrayList<String> getDates() {
        return Dates;
    }

    public void setDates(ArrayList<String> dates) {
        Dates = dates;
    }
}
