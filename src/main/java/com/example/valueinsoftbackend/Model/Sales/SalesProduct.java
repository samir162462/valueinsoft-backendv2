/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model.Sales;

public class SalesProduct {
    String itemName;
    int numOfOrder;
    int sumQuantity;
    int sumTotal;

    public SalesProduct(String itemName, int numOfOrder, int sumQuantity, int sumTotal) {
        this.itemName = itemName;
        this.numOfOrder = numOfOrder;
        this.sumQuantity = sumQuantity;
        this.sumTotal = sumTotal;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getNumOfOrder() {
        return numOfOrder;
    }

    public void setNumOfOrder(int numOfOrder) {
        this.numOfOrder = numOfOrder;
    }

    public int getSumQuantity() {
        return sumQuantity;
    }

    public void setSumQuantity(int sumQuantity) {
        this.sumQuantity = sumQuantity;
    }

    public int getSumTotal() {
        return sumTotal;
    }

    public void setSumTotal(int sumTotal) {
        this.sumTotal = sumTotal;
    }
}
