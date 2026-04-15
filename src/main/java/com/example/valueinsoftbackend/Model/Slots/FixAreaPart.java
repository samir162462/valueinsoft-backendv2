package com.example.valueinsoftbackend.Model.Slots;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FixAreaPart {
    private int id;
    private int faId;
    private int productId;
    private int quantity;
    private int unitPrice;
    private int total;

    @JsonProperty("isDeducted")
    private boolean isDeducted;
    
    // Additional transient fields useful for UI mapping
    private String productName;

    public FixAreaPart() {
    }

    public FixAreaPart(int id, int faId, int productId, int quantity, int unitPrice, int total, boolean isDeducted) {
        this.id = id;
        this.faId = faId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.total = total;
        this.isDeducted = isDeducted;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getFaId() {
        return faId;
    }

    public void setFaId(int faId) {
        this.faId = faId;
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

    public int getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(int unitPrice) {
        this.unitPrice = unitPrice;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public boolean isDeducted() {
        return isDeducted;
    }

    public void setDeducted(boolean isDeducted) {
        this.isDeducted = isDeducted;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
}
