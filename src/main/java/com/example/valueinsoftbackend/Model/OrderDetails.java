package com.example.valueinsoftbackend.Model;

public class OrderDetails {

    int itemId;
    String itemName;
    int quantity;
    int price ;
    int total;



    public OrderDetails(int itemId, String itemName, int quantity, int price, int total) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.total = total;
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getPrice() {
        return price;
    }

    public int getTotal() {
        return total;
    }

    public int getItemId() {
        return itemId;
    }

    @Override
    public String toString() {
        return "OrderDetails{" +
                "itemId=" + itemId +
                ", itemName='" + itemName + '\'' +
                ", quantity=" + quantity +
                ", price=" + price +
                ", total=" + total +
                '}';
    }
}
