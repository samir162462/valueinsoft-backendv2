package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

public class SupplierProductCreateRequest {

    @Positive(message = "quantity must be positive")
    private int quantity;

    @Positive(message = "cost must be positive")
    private int cost;

    @NotBlank(message = "userName is required")
    @Size(max = 60, message = "userName must be 60 characters or fewer")
    private String userName;

    @PositiveOrZero(message = "sPaid must be zero or greater")
    private int sPaid;

    @NotBlank(message = "time is required")
    private String time;

    @Size(max = 120, message = "desc must be 120 characters or fewer")
    private String desc;

    @Positive(message = "orderDetailsId must be positive")
    private int orderDetailsId;

    public SupplierProductCreateRequest() {
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

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public int getOrderDetailsId() {
        return orderDetailsId;
    }

    public void setOrderDetailsId(int orderDetailsId) {
        this.orderDetailsId = orderDetailsId;
    }
}
