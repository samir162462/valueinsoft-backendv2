package com.example.valueinsoftbackend.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Offer {
    @PositiveOrZero
    private int offerId;
    
    @PositiveOrZero
    private int branchId;
    
    @NotBlank
    private String offerName;
    
    private String offerDescription;
    
    @NotBlank
    private String offerType; // PERCENTAGE, FIXED, BOGO
    
    private double offerValue;
    
    private double minOrderTotal;
    
    private String applicableItems; // JSON string: {"product_ids": [], "category_ids": []}
    
    private int minQuantity;
    
    @JsonProperty("isActive")
    private boolean isActive;
    
    private Timestamp startDate;
    
    private Timestamp endDate;
    
    private Timestamp createdAt;
    
    private Timestamp updatedAt;
}
