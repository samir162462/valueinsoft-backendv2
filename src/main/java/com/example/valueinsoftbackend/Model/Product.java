package com.example.valueinsoftbackend.Model;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Product {
    int productId;
    String productName;
    Timestamp buyingDay;
    String activationPeriod;
    int rPrice;
    int lPrice;
    int bPrice;
    String companyName;
    String type;
    String ownerName;
    String serial;
    String desc;
    int batteryLife;
    String ownerPhone;
    String ownerNI;
    int quantity;
    String pState;
    int supplierId;
    String major;
    String image;

}
