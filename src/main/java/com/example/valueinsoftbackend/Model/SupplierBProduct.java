/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;

public class SupplierBProduct {

    int sBPId;
    int productId;
    int quantity;
    int cost;
    String userName;
    int sPaid;
    Timestamp time;
    String desc;
    int orderDetailsId;


    public SupplierBProduct(int sBPId, int productId, int quantity, int cost, String userName, int sPaid, Timestamp time, String desc, int orderDetailsId) {
        this.sBPId = sBPId;
        this.productId = productId;
        this.quantity = quantity;
        this.cost = cost;
        this.userName = userName;
        this.sPaid = sPaid;
        this.time = time;
        this.desc = desc;
        this.orderDetailsId = orderDetailsId;
    }

    public int getsBPId() {
        return sBPId;
    }

    public void setsBPId(int sBPId) {
        this.sBPId = sBPId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int getOrderDetailsId() {
        return orderDetailsId;
    }

    public void setOrderDetailsId(int orderDetailsId) {
        this.orderDetailsId = orderDetailsId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public int getsPaid() {
        return sPaid;
    }

    public void setsPaid(int sPaid) {
        this.sPaid = sPaid;
    }

    public Timestamp getTime() {
        return time;
    }

    public void setTime(Timestamp time) {
        this.time = time;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "SupplierBProduct{" +
                "sBPId=" + sBPId +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", cost=" + cost +
                ", userName='" + userName + '\'' +
                ", sPaid=" + sPaid +
                ", time=" + time +
                ", desc='" + desc + '\'' +
                ", orderDetailsId=" + orderDetailsId +
                '}';
    }
}

