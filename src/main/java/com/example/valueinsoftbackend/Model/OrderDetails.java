package com.example.valueinsoftbackend.Model;

import java.util.ArrayList;

public class OrderDetails {

    int odId;
    int itemId;
    String itemName;
    int quantity;
    int price ;
    int total;
    int productId;
    int bouncedBack;

    public OrderDetails(int odId, int itemId, String itemName, int quantity, int price, int total, int productId, int bouncedBack) {
        this.odId = odId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.total = total;
        this.productId = productId;
        this.bouncedBack = bouncedBack;
    }

    public int getOdId() {
        return odId;
    }

    public void setOdId(int odId) {
        this.odId = odId;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getBouncedBack() {
        return bouncedBack;
    }

    public void setBouncedBack(int bouncedBack) {
        this.bouncedBack = bouncedBack;
    }
}
