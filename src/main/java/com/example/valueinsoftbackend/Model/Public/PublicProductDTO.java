package com.example.valueinsoftbackend.Model.Public;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicProductDTO {
    private Long productId;
    private String productName;
    private String description;
    private String imageUrl;
    private String category;
    private Integer sellingPrice;
    private Integer retailPrice;
    private BigDecimal offerPrice;
    private Integer quantity;
    private boolean isAvailable;
}
