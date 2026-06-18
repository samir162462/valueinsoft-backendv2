package com.example.valueinsoftbackend.Model;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.ArrayList;

public class Order {
    int orderId ;
    Timestamp orderTime;
    String clientName;
    String orderType ;
    int orderDiscount;
    int orderTotal;
    String salesUser;
    int branchId;
    int clientId;
    int orderIncome;
    int totalBouncedBack;
    Integer requestedShiftId;
    Long loyaltyRedemptionId;
    int loyaltyPointsRedeemed;
    int loyaltyPointsEarned;
    BigDecimal loyaltyDiscountAmount;
    BigDecimal loyaltyNetAmount;
    ArrayList<OrderDetails> orderDetails;




    public Order(int orderId, Timestamp orderTime, String clientName, String orderType, int orderDiscount, int orderTotal, String salesUser, int branchId, int clientId, int orderIncome, int totalBouncedBack, ArrayList<OrderDetails> orderDetails) {
        this.orderId = orderId;
        this.orderTime = orderTime;
        this.clientName = clientName;
        this.orderType = orderType;
        this.orderDiscount = orderDiscount;
        this.orderTotal = orderTotal;
        this.salesUser = salesUser;
        this.branchId = branchId;
        this.clientId = clientId;
        this.orderIncome = orderIncome;
        this.totalBouncedBack = totalBouncedBack;
        this.requestedShiftId = null;
        this.loyaltyRedemptionId = null;
        this.loyaltyPointsRedeemed = 0;
        this.loyaltyPointsEarned = 0;
        this.loyaltyDiscountAmount = BigDecimal.ZERO;
        this.loyaltyNetAmount = null;
        this.orderDetails = orderDetails;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public Timestamp getOrderTime() {
        return orderTime;
    }

    public void setOrderTime(Timestamp orderTime) {
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

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public int getOrderIncome() {
        return orderIncome;
    }

    public void setOrderIncome(int orderIncome) {
        this.orderIncome = orderIncome;
    }

    public int getTotalBouncedBack() {
        return totalBouncedBack;
    }

    public void setTotalBouncedBack(int totalBouncedBack) {
        this.totalBouncedBack = totalBouncedBack;
    }

    public Integer getRequestedShiftId() {
        return requestedShiftId;
    }

    public void setRequestedShiftId(Integer requestedShiftId) {
        this.requestedShiftId = requestedShiftId;
    }

    public Long getLoyaltyRedemptionId() {
        return loyaltyRedemptionId;
    }

    public void setLoyaltyRedemptionId(Long loyaltyRedemptionId) {
        this.loyaltyRedemptionId = loyaltyRedemptionId;
    }

    public int getLoyaltyPointsRedeemed() {
        return loyaltyPointsRedeemed;
    }

    public void setLoyaltyPointsRedeemed(int loyaltyPointsRedeemed) {
        this.loyaltyPointsRedeemed = loyaltyPointsRedeemed;
    }

    public int getLoyaltyPointsEarned() {
        return loyaltyPointsEarned;
    }

    public void setLoyaltyPointsEarned(int loyaltyPointsEarned) {
        this.loyaltyPointsEarned = loyaltyPointsEarned;
    }

    public BigDecimal getLoyaltyDiscountAmount() {
        return loyaltyDiscountAmount;
    }

    public void setLoyaltyDiscountAmount(BigDecimal loyaltyDiscountAmount) {
        this.loyaltyDiscountAmount = loyaltyDiscountAmount;
    }

    public BigDecimal getLoyaltyNetAmount() {
        return loyaltyNetAmount;
    }

    public void setLoyaltyNetAmount(BigDecimal loyaltyNetAmount) {
        this.loyaltyNetAmount = loyaltyNetAmount;
    }

    public ArrayList<OrderDetails> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(ArrayList<OrderDetails> orderDetails) {
        this.orderDetails = orderDetails;
    }
}
