package com.example.valueinsoftbackend.Model.Request;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.List;

public class CreateOrderRequest {

    @PositiveOrZero(message = "orderId must be zero or greater")
    private int orderId;

    private String orderTime;

    private String clientName;

    @NotBlank(message = "orderType is required")
    private String orderType;

    @Min(value = 0, message = "orderDiscount must be zero or greater")
    private int orderDiscount;

    @Min(value = 0, message = "orderTotal must be zero or greater")
    private int orderTotal;

    @NotBlank(message = "salesUser is required")
    private String salesUser;

    @Positive(message = "branchId must be positive")
    private int branchId;

    @PositiveOrZero(message = "clientId must be zero or greater")
    private int clientId;

    @Min(value = 0, message = "orderIncome must be zero or greater")
    private int orderIncome;

    @Valid
    @NotEmpty(message = "orderDetails must contain at least one item")
    private List<OrderItemRequest> orderDetails = new ArrayList<>();

    public CreateOrderRequest() {
    }

    public int getOrderId() {
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

    public List<OrderItemRequest> getOrderDetails() {
        return orderDetails;
    }

    public void setOrderDetails(List<OrderItemRequest> orderDetails) {
        this.orderDetails = orderDetails;
    }
}
