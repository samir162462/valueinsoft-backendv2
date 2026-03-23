package com.example.valueinsoftbackend.Model.ResponseModel;

import com.example.valueinsoftbackend.Model.Product;

public class ProductOperationResponse {

    private String title;
    private long id;
    private int numItems;
    private int transTotal;
    private String transactionType;
    private Product product;

    public ProductOperationResponse(String title, long id, int numItems, int transTotal, String transactionType, Product product) {
        this.title = title;
        this.id = id;
        this.numItems = numItems;
        this.transTotal = transTotal;
        this.transactionType = transactionType;
        this.product = product;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getNumItems() {
        return numItems;
    }

    public void setNumItems(int numItems) {
        this.numItems = numItems;
    }

    public int getTransTotal() {
        return transTotal;
    }

    public void setTransTotal(int transTotal) {
        this.transTotal = transTotal;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }
}
