package com.example.valueinsoftbackend.Model;

import java.util.ArrayList;

public class Order {
    int orderId ;
    String orderTime;
    String clientName;
    String orderType ;
    int orderDiscount;
    int orderTotal;
    String salesUser;
    int branchId;
    ArrayList<OrderDetails> orderDetails;


    public Order(int orderId, String orderTime, String clientName, String orderType, int orderDiscount, int orderTotal, String salesUser, int branchId, ArrayList<OrderDetails> orderDetails) {
        this.orderId = orderId;
        this.orderTime = orderTime;
        this.clientName = clientName;
        this.orderType = orderType;
        this.orderDiscount = orderDiscount;
        this.orderTotal = orderTotal;
        this.salesUser = salesUser;
        this.branchId = branchId;
        this.orderDetails = orderDetails;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(String orderTime) {
        this.orderTime = orderTime;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public int getOrderDiscount() {
        return orderDiscount;
    }

    public void setOrderDiscount(int orderDiscount) {
        this.orderDiscount = orderDiscount;
    }

    public int getOrderTotal() {
        return orderTotal;
    }

    public void setOrderTotal(int orderTotal) {
        this.orderTotal = orderTotal;
    }

    public String getSalesUser() {
        return salesUser;
    }

    public void setSalesUser(String salesUser) {
        this.salesUser = salesUser;
    }

    public int getBranchId() {
        return branchId;
    }

    public void setBranchId(int branchId) {
        this.branchId = branchId;
    }

    public ArrayList<OrderDetails> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(ArrayList<OrderDetails> orderDetails) {
        this.orderDetails = orderDetails;
    }
}
