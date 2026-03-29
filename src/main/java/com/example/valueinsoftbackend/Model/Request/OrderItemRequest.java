package com.example.valueinsoftbackend.Model.Request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;

public class OrderItemRequest {

    @Positive(message = "itemId must be positive")
    private int itemId;

    @NotBlank(message = "itemName is required")
    private String itemName;

    @Positive(message = "quantity must be positive")
    private int quantity;

    @Min(value = 0, message = "price must be zero or greater")
    private int price;

    @Min(value = 0, message = "total must be zero or greater")
    private int total;

    @Positive(message = "productId must be positive")
    private int productId;

    public OrderItemRequest() {
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
}
