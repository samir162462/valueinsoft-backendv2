package com.example.valueinsoftbackend.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Product {
    int productId;
    String productName;
    Timestamp buyingDay;
    String activationPeriod;
    @JsonProperty("rPrice")
    int rPrice;
    @JsonProperty("lPrice")
    int lPrice;
    @JsonProperty("bPrice")
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
